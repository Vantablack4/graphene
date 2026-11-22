package tytoo.grapheneui.internal.cef;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.resource.GrapheneMimeTypes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Answers one proxied-asset request with a plain blocking GET on one of {@link #WORKERS} dedicated threads.
 * <ul>
 *     <li>The worker thread is the request's slot: it reads the whole body (at most {@link #MAX_BODY_BYTES}, refused
 *     at the headers when declared larger) within {@link #DEADLINE}, closes the upstream stream, and frees the slot
 *     by returning. Up to {@link #MAX_QUEUED} more requests wait in the pool's queue; beyond that a local 503.</li>
 *     <li>Every byte a body holds, while it is read and until CEF has taken it, comes out of one
 *     {@link #BUDGET_BYTES} budget reserved before reading; a request that does not fit gets a local 503.</li>
 *     <li>A delivered body is dropped as soon as CEF has copied its last byte, on a CEF cancel, or after
 *     {@link #DELIVERY_DEADLINE} if CEF stops reading.</li>
 *     <li>CEF's thread only validates and submits: every answer is issued from a worker or the proxy's timer
 *     thread.</li>
 * </ul>
 * Nothing from the page is forwarded (no cookies, headers or query); the type comes from the extension; any upstream
 * failure is a local error.
 */
final class GrapheneProxiedAssetHandler extends CefResourceHandlerAdapter {
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    static final int WORKERS = 16;
    static final int MAX_QUEUED = 512;
    static final long BUDGET_BYTES = 160L * 1024 * 1024;
    static final Duration DEADLINE = Duration.ofSeconds(30);
    static final Duration DELIVERY_DEADLINE = Duration.ofSeconds(60);
    static final String NOSNIFF = "nosniff";
    static final String SANDBOX = "sandbox";
    static final int STATUS_OK = 200;
    static final int STATUS_NOT_FOUND = 404;
    static final int STATUS_METHOD_NOT_ALLOWED = 405;
    static final int STATUS_BAD_GATEWAY = 502;
    static final int STATUS_UNAVAILABLE = 503;
    static final int STATUS_GATEWAY_TIMEOUT = 504;
    static final String WORKER_THREAD_NAME = "graphene-proxy-";
    static final String TIMER_THREAD_NAME = "graphene-proxy-timer";

    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneProxiedAssetHandler.class);
    private static final String METHOD_GET = "GET";
    private static final String TEXT_PLAIN = "text/plain";
    private static final int CHUNK_BYTES = 64 * 1024;
    private static final ThreadPoolExecutor POOL = workerPool(WORKERS, MAX_QUEUED);
    private static final ScheduledExecutorService TIMER = timer();
    private static final GrapheneProxyBodyBudget BUDGET = new GrapheneProxyBodyBudget(BUDGET_BYTES);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String upstreamUrl;
    private final String mimeType;
    private final Opener opener;
    private final ThreadPoolExecutor pool;
    private final ScheduledExecutorService timer;
    private final GrapheneProxyBodyBudget budget;
    private final Duration deadline;
    private final Duration deliveryDeadline;
    private final Object lock = new Object();
    private Runnable onReady;
    private Future<?> task;
    private Thread worker;
    private InputStream stream;
    private ScheduledFuture<?> deadlineTask;
    private ScheduledFuture<?> deliveryTask;
    private boolean cancelled;
    private boolean timedOut;
    private long reservedBytes;
    private byte[] body;
    private int bodyLength;
    private int readOffset;
    private volatile int status = STATUS_BAD_GATEWAY;

    GrapheneProxiedAssetHandler(String upstreamUrl) {
        this(upstreamUrl, GrapheneProxiedAssetHandler::openUpstream, POOL, TIMER, BUDGET, DEADLINE, DELIVERY_DEADLINE);
    }

    GrapheneProxiedAssetHandler(
            String upstreamUrl,
            Opener opener,
            ThreadPoolExecutor pool,
            ScheduledExecutorService timer,
            GrapheneProxyBodyBudget budget,
            Duration deadline,
            Duration deliveryDeadline
    ) {
        this.upstreamUrl = Objects.requireNonNull(upstreamUrl, "upstreamUrl");
        this.mimeType = GrapheneMimeTypes.resolve(upstreamUrl);
        this.opener = Objects.requireNonNull(opener, "opener");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.timer = Objects.requireNonNull(timer, "timer");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.deliveryDeadline = Objects.requireNonNull(deliveryDeadline, "deliveryDeadline");
    }

    static int localStatusFor(int upstreamStatus) {
        if (upstreamStatus == STATUS_OK) {
            return STATUS_OK;
        }

        return upstreamStatus == STATUS_NOT_FOUND ? STATUS_NOT_FOUND : STATUS_BAD_GATEWAY;
    }

    static ThreadPoolExecutor workerPool(int workers, int maxQueued) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, WORKER_THREAD_NAME + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueued),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    static ScheduledExecutorService timer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, TIMER_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        return start(request.getMethod(), callback::Continue);
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setStatus(status);
        response.setMimeType(status == STATUS_OK ? mimeType : TEXT_PLAIN);
        response.setHeaderByName("X-Content-Type-Options", NOSNIFF, true);
        response.setHeaderByName("Content-Security-Policy", SANDBOX, true);
        synchronized (lock) {
            responseLength.set(status == STATUS_OK ? bodyLength : 0);
        }
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        synchronized (lock) {
            byte[] bytes = body;
            if (status != STATUS_OK || bytes == null || readOffset >= bytes.length) {
                bytesRead.set(0);
                return false;
            }

            int count = Math.min(bytesToRead, bytes.length - readOffset);
            System.arraycopy(bytes, readOffset, dataOut, 0, count);
            readOffset += count;
            bytesRead.set(count);
            if (readOffset >= bytes.length) {
                dropBodyLocked();
            }

            return true;
        }
    }

    @Override
    public void cancel() {
        Future<?> queued;
        synchronized (lock) {
            cancelled = true;
            queued = task;
            if (worker != null) {
                closeStreamLocked();
                worker.interrupt();
            }

            dropBodyLocked();
        }

        if (queued != null && queued.cancel(false) && queued instanceof Runnable runnable) {
            pool.remove(runnable);
        }
    }

    /**
     * Submits the fetch and calls {@code onReady} once the status and body are known, from a worker thread, or from
     * the timer thread for a local refusal (another method, or a full queue); never from the calling thread. Returns
     * {@code true}: the request is always handled here.
     */
    boolean start(String method, Runnable onReady) {
        synchronized (lock) {
            this.onReady = Objects.requireNonNull(onReady, "onReady");
        }

        if (!METHOD_GET.equalsIgnoreCase(method)) {
            answerFromTimer(STATUS_METHOD_NOT_ALLOWED);
            return true;
        }

        try {
            Future<?> submitted = pool.submit(this::fetch);
            synchronized (lock) {
                task = submitted;
            }
        } catch (RejectedExecutionException ignored) {
            // The queue is full: a local 503, answered off the calling thread.
            answerFromTimer(STATUS_UNAVAILABLE);
        }

        return true;
    }

    int status() {
        return status;
    }

    boolean holdsBody() {
        synchronized (lock) {
            return body != null || reservedBytes > 0;
        }
    }

    private void answerFromTimer(int localStatus) {
        status = localStatus;
        Runnable ready;
        synchronized (lock) {
            ready = onReady;
        }
        timer.execute(ready);
    }

    private void fetch() {
        synchronized (lock) {
            if (cancelled) {
                return;
            }

            worker = Thread.currentThread();
            deadlineTask = timer.schedule(this::onDeadline, deadline.toMillis(), TimeUnit.MILLISECONDS);
        }

        int result;
        try {
            result = fetchBody();
        } catch (IOException | InterruptedException | RuntimeException failure) {
            synchronized (lock) {
                result = timedOut ? STATUS_GATEWAY_TIMEOUT : STATUS_BAD_GATEWAY;
            }
            DEBUG_LOGGER.debug("Proxied asset failed url={} status={} reason={}", upstreamUrl, result, failure.toString());
        } finally {
            synchronized (lock) {
                closeStreamLocked();
                deadlineTask.cancel(false);
                worker = null;
            }
            Thread.interrupted();
        }

        Runnable ready;
        synchronized (lock) {
            if (cancelled) {
                dropBodyLocked();
                return;
            }

            status = result;
            if (result != STATUS_OK) {
                dropBodyLocked();
            } else {
                deliveryTask = timer.schedule(this::onDeliveryDeadline, deliveryDeadline.toMillis(), TimeUnit.MILLISECONDS);
            }
            ready = onReady;
        }

        ready.run();
    }

    private int fetchBody() throws IOException, InterruptedException {
        Upstream upstream = opener.open(upstreamUrl, deadline);
        synchronized (lock) {
            stream = upstream.body();
            if (cancelled || timedOut) {
                throw new IOException("the request ended while its headers arrived");
            }
        }

        int localStatus = localStatusFor(upstream.status());
        if (localStatus != STATUS_OK) {
            return localStatus;
        }

        long declaredLength = upstream.contentLength();
        if (declaredLength > MAX_BODY_BYTES) {
            return STATUS_BAD_GATEWAY;
        }

        // An unknown length is read in chunks and copied once into an exact array, so it may briefly need twice its size.
        long reservation = declaredLength >= 0 ? declaredLength : 2L * MAX_BODY_BYTES;
        if (!budget.tryReserve(reservation)) {
            return STATUS_UNAVAILABLE;
        }

        synchronized (lock) {
            reservedBytes = reservation;
        }

        byte[] bytes = declaredLength >= 0
                ? readExactly(upstream.body(), (int) declaredLength)
                : readUpToCap(upstream.body());
        if (bytes == null) {
            return STATUS_BAD_GATEWAY;
        }

        synchronized (lock) {
            budget.release(reservedBytes - bytes.length);
            reservedBytes = bytes.length;
            body = bytes;
            bodyLength = bytes.length;
            readOffset = 0;
        }

        return STATUS_OK;
    }

    private void onDeadline() {
        synchronized (lock) {
            if (worker == null) {
                return;
            }

            timedOut = true;
            closeStreamLocked();
            worker.interrupt();
        }
    }

    private void onDeliveryDeadline() {
        synchronized (lock) {
            dropBodyLocked();
        }
    }

    private void closeStreamLocked() {
        InputStream open = stream;
        stream = null;
        if (open == null) {
            return;
        }

        try {
            open.close();
        } catch (IOException ignored) {
            // Closing only ends the exchange; a failure to close changes nothing for the answer.
        }
    }

    private void dropBodyLocked() {
        body = null;
        if (reservedBytes > 0) {
            budget.release(reservedBytes);
            reservedBytes = 0;
        }

        if (deliveryTask != null) {
            deliveryTask.cancel(false);
            deliveryTask = null;
        }
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        if (input.readNBytes(bytes, 0, length) != length || input.read() != -1) {
            return null;
        }

        return bytes;
    }

    private static byte[] readUpToCap(InputStream input) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        int total = 0;
        while (true) {
            byte[] chunk = input.readNBytes(CHUNK_BYTES);
            if (chunk.length == 0) {
                break;
            }

            total += chunk.length;
            if (total > MAX_BODY_BYTES) {
                return null;
            }

            chunks.add(chunk);
        }

        byte[] bytes = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, bytes, offset, chunk.length);
            offset += chunk.length;
        }

        return bytes;
    }

    private static Upstream openUpstream(String upstreamUrl, Duration deadline) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(upstreamUrl))
                .GET()
                .timeout(deadline)
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        return new Upstream(response.statusCode(), declaredLength, response.body());
    }

    /**
     * Opens one upstream exchange and returns once its headers have arrived.
     */
    @FunctionalInterface
    interface Opener {
        Upstream open(String upstreamUrl, Duration deadline) throws IOException, InterruptedException;
    }

    record Upstream(int status, long contentLength, InputStream body) {
    }
}
