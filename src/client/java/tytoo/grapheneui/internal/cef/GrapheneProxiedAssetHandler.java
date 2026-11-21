package tytoo.grapheneui.internal.cef;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.resource.GrapheneMimeTypes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Answers one proxied-asset request: a GET of a fixed upstream URL. Nothing from the page is forwarded (no cookies,
 * headers or query); the type comes from the extension; the body is capped at {@link #MAX_BODY_BYTES} and must
 * arrive whole within {@link #DEADLINE} of the fetch starting. A request holds one of {@link #MAX_RUNNING} slots from
 * the start of its fetch until CEF has read its body (or it is cancelled), so at most that many bodies are ever
 * buffered; up to {@link #MAX_QUEUED} more wait their turn, and only beyond that a request gets a local 503. Every
 * start, cancel and continuation runs on Graphene's own small pool, never on CEF's IO thread. Any upstream failure is
 * a local error.
 */
final class GrapheneProxiedAssetHandler extends CefResourceHandlerAdapter {
    static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    static final int MAX_RUNNING = 32;
    static final int MAX_QUEUED = 512;
    static final Duration DEADLINE = Duration.ofSeconds(30);
    static final String NOSNIFF = "nosniff";
    static final String SANDBOX = "sandbox";
    static final int STATUS_OK = 200;
    static final int STATUS_NOT_FOUND = 404;
    static final int STATUS_METHOD_NOT_ALLOWED = 405;
    static final int STATUS_BAD_GATEWAY = 502;
    static final int STATUS_UNAVAILABLE = 503;
    static final int STATUS_GATEWAY_TIMEOUT = 504;
    static final String THREAD_NAME = "graphene-asset-proxy";

    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneProxiedAssetHandler.class);
    private static final String METHOD_GET = "GET";
    private static final String TEXT_PLAIN = "text/plain";
    private static final byte[] EMPTY = new byte[0];
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(EXECUTOR)
            .build();
    private static final GrapheneProxyQueue QUEUE = new GrapheneProxyQueue(MAX_RUNNING, MAX_QUEUED, EXECUTOR);

    private final String upstreamUrl;
    private final String mimeType;
    private final Function<String, CompletableFuture<Fetched>> fetcher;
    private final GrapheneProxyQueue queue;
    private final Executor executor;
    private final Duration deadline;
    private final Object lock = new Object();
    private State state = State.NEW;
    private GrapheneProxyQueue.Ticket ticket;
    private CompletableFuture<Fetched> fetch;
    private Runnable onReady;
    private volatile int status = STATUS_BAD_GATEWAY;
    private volatile byte[] body = EMPTY;
    private int readOffset;

    GrapheneProxiedAssetHandler(String upstreamUrl) {
        this(upstreamUrl, GrapheneProxiedAssetHandler::fetchUpstream, QUEUE, EXECUTOR, DEADLINE);
    }

    GrapheneProxiedAssetHandler(
            String upstreamUrl,
            Function<String, CompletableFuture<Fetched>> fetcher,
            GrapheneProxyQueue queue,
            Executor executor,
            Duration deadline
    ) {
        this.upstreamUrl = Objects.requireNonNull(upstreamUrl, "upstreamUrl");
        this.mimeType = GrapheneMimeTypes.resolve(upstreamUrl);
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    static int localStatusFor(int upstreamStatus) {
        if (upstreamStatus == STATUS_OK) {
            return STATUS_OK;
        }

        return upstreamStatus == STATUS_NOT_FOUND ? STATUS_NOT_FOUND : STATUS_BAD_GATEWAY;
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
        responseLength.set(status == STATUS_OK ? body.length : 0);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        byte[] bytes = status == STATUS_OK ? body : EMPTY;
        int remaining = bytes.length - readOffset;
        if (remaining <= 0) {
            bytesRead.set(0);
            executor.execute(this::close);
            return false;
        }

        int count = Math.min(bytesToRead, remaining);
        System.arraycopy(bytes, readOffset, dataOut, 0, count);
        readOffset += count;
        bytesRead.set(count);
        return true;
    }

    @Override
    public void cancel() {
        executor.execute(this::close);
    }

    /**
     * Queues the fetch and calls {@code onReady} once the status and body are known, on Graphene's own pool (or at
     * once for a local refusal: another method, or a full queue). Returns {@code true}: the request is always handled
     * here.
     */
    boolean start(String method, Runnable onReady) {
        if (!METHOD_GET.equalsIgnoreCase(method)) {
            status = STATUS_METHOD_NOT_ALLOWED;
            onReady.run();
            return true;
        }

        synchronized (lock) {
            this.onReady = Objects.requireNonNull(onReady, "onReady");
            state = State.QUEUED;
            ticket = queue.submit(this::begin);
            if (ticket != null) {
                return true;
            }

            state = State.CLOSED;
        }

        status = STATUS_UNAVAILABLE;
        onReady.run();
        return true;
    }

    int status() {
        return status;
    }

    byte[] body() {
        return body;
    }

    /**
     * Ends the request: the slot goes back exactly once, a running exchange is cancelled, and the body is dropped.
     * During startup it only marks the request; {@link #begin} honours that once the exchange exists.
     */
    void close() {
        CompletableFuture<Fetched> running;
        GrapheneProxyQueue.Ticket owned;
        synchronized (lock) {
            if (state == State.CLOSED) {
                return;
            }

            if (state == State.STARTING) {
                state = State.CANCELLING;
                return;
            }

            running = fetch;
            owned = ticket;
            fetch = null;
            ticket = null;
            state = State.CLOSED;
        }

        body = EMPTY;
        if (running != null) {
            running.cancel(true);
        }

        if (owned != null) {
            owned.release();
        }
    }

    private void begin(GrapheneProxyQueue.Ticket grantedTicket) {
        synchronized (lock) {
            if (state != State.QUEUED) {
                grantedTicket.release();
                return;
            }

            ticket = grantedTicket;
            state = State.STARTING;
        }

        CompletableFuture<Fetched> started;
        try {
            started = fetcher.apply(upstreamUrl);
        } catch (RuntimeException failure) {
            started = CompletableFuture.failedFuture(failure);
        }

        boolean cancelledDuringStartup;
        synchronized (lock) {
            fetch = started;
            cancelledDuringStartup = state == State.CANCELLING;
            state = State.RUNNING;
        }

        if (cancelledDuringStartup) {
            close();
            return;
        }

        started.orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .whenCompleteAsync(this::finish, executor);
    }

    private void finish(Fetched fetched, Throwable failure) {
        Runnable ready;
        boolean nothingToDeliver;
        synchronized (lock) {
            if (state != State.RUNNING) {
                return;
            }

            CompletableFuture<Fetched> running = fetch;
            fetch = null;
            if (failure != null || fetched == null) {
                Throwable cause = failure != null && failure.getCause() != null ? failure.getCause() : failure;
                status = cause instanceof TimeoutException ? STATUS_GATEWAY_TIMEOUT : STATUS_BAD_GATEWAY;
                body = EMPTY;
                if (running != null) {
                    running.cancel(true);
                }
                DEBUG_LOGGER.debug("Proxied asset failed url={} status={} reason={}", upstreamUrl, status, cause);
            } else {
                int localStatus = localStatusFor(fetched.status());
                boolean complete = localStatus != STATUS_OK || fetched.body() != null;
                status = complete ? localStatus : STATUS_BAD_GATEWAY;
                body = status == STATUS_OK ? fetched.body() : EMPTY;
            }

            nothingToDeliver = body.length == 0;
            state = State.DELIVERING;
            ready = onReady;
        }

        if (nothingToDeliver) {
            close();
        }

        ready.run();
    }

    private static CompletableFuture<Fetched> fetchUpstream(String upstreamUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(upstreamUrl))
                .GET()
                .timeout(DEADLINE)
                .build();
        CompletableFuture<HttpResponse<byte[]>> send = CLIENT.sendAsync(request, GrapheneProxiedAssetHandler::cappedBody);
        CompletableFuture<Fetched> fetched = send.thenApplyAsync(
                response -> new Fetched(response.statusCode(), response.body()),
                EXECUTOR
        );
        // A deadline, a cancel or any failure ends the exchange too, so no stream stays open.
        fetched.whenCompleteAsync((ignoredResult, failure) -> {
            if (failure != null) {
                send.cancel(true);
            }
        }, EXECUTOR);
        return fetched;
    }

    /**
     * Reads a 200 body up to {@link #MAX_BODY_BYTES}. A body declared larger, any other status, or a body that grows
     * past the cap gives {@code null}, and the exchange stops there instead of draining the rest.
     */
    static HttpResponse.BodySubscriber<byte[]> cappedBody(HttpResponse.ResponseInfo responseInfo) {
        long declaredLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);
        if (responseInfo.statusCode() != STATUS_OK || declaredLength > MAX_BODY_BYTES) {
            return new CappedBodySubscriber(-1);
        }

        return new CappedBodySubscriber(MAX_BODY_BYTES);
    }

    private enum State {
        NEW,
        QUEUED,
        STARTING,
        CANCELLING,
        RUNNING,
        DELIVERING,
        CLOSED
    }

    record Fetched(int status, byte[] body) {
    }

    /**
     * Collects a body without blocking, and gives {@code null} as soon as it grows past its cap; a negative cap refuses
     * the body outright and cancels the exchange before reading any of it.
     */
    static final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        CappedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (maxBytes < 0) {
                result.complete(null);
                subscription.cancel();
                return;
            }

            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) {
                return;
            }

            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > maxBytes) {
                    result.complete(null);
                    subscription.cancel();
                    return;
                }

                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable instanceof IOException ? throwable : new IOException(throwable));
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
