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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Answers one proxied-asset request: a GET of a fixed upstream URL. Nothing from the page is forwarded (no cookies,
 * headers or query); the type comes from the extension; the body is capped and must arrive whole within a deadline;
 * at most a few requests are in flight, the rest get a local 503; every continuation runs on Graphene's own small
 * pool, never on CEF's IO thread; any upstream failure is a local error.
 */
final class GrapheneProxiedAssetHandler extends CefResourceHandlerAdapter {
    static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    static final int MAX_IN_FLIGHT = 32;
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
    private static final Semaphore SLOTS = new Semaphore(MAX_IN_FLIGHT);

    private final String upstreamUrl;
    private final String mimeType;
    private final Function<String, CompletableFuture<Fetched>> fetcher;
    private final Semaphore slots;
    private final Duration deadline;
    private final AtomicBoolean slotHeld = new AtomicBoolean();
    private volatile int status = STATUS_BAD_GATEWAY;
    private volatile byte[] body = EMPTY;
    private volatile CompletableFuture<Fetched> fetch;
    private volatile boolean cancelled;
    private int readOffset;

    GrapheneProxiedAssetHandler(String upstreamUrl) {
        this(upstreamUrl, GrapheneProxiedAssetHandler::fetchUpstream, SLOTS, DEADLINE);
    }

    GrapheneProxiedAssetHandler(
            String upstreamUrl,
            Function<String, CompletableFuture<Fetched>> fetcher,
            Semaphore slots,
            Duration deadline
    ) {
        this.upstreamUrl = Objects.requireNonNull(upstreamUrl, "upstreamUrl");
        this.mimeType = GrapheneMimeTypes.resolve(upstreamUrl);
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.slots = Objects.requireNonNull(slots, "slots");
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
        cancelled = true;
        CompletableFuture<Fetched> pending = fetch;
        if (pending != null) {
            pending.cancel(true);
        }

        body = EMPTY;
        releaseSlot();
    }

    /**
     * Starts the fetch and calls {@code onReady} once the status and body are known, on Graphene's own pool (or at
     * once for a local refusal). Returns {@code true}: the request is always handled here.
     */
    boolean start(String method, Runnable onReady) {
        if (!METHOD_GET.equalsIgnoreCase(method)) {
            status = STATUS_METHOD_NOT_ALLOWED;
            onReady.run();
            return true;
        }

        if (!slots.tryAcquire()) {
            status = STATUS_UNAVAILABLE;
            onReady.run();
            return true;
        }

        slotHeld.set(true);
        CompletableFuture<Fetched> pending;
        try {
            pending = fetcher.apply(upstreamUrl);
        } catch (RuntimeException failure) {
            pending = CompletableFuture.failedFuture(failure);
        }

        fetch = pending;
        pending.orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .handleAsync((fetched, failure) -> {
                    finish(fetched, failure);
                    return null;
                }, EXECUTOR)
                .whenCompleteAsync((ignoredResult, ignoredFailure) -> {
                    if (!cancelled) {
                        onReady.run();
                    }
                }, EXECUTOR);
        return true;
    }

    int status() {
        return status;
    }

    byte[] body() {
        return body;
    }

    private void finish(Fetched fetched, Throwable failure) {
        try {
            if (failure != null || fetched == null) {
                Throwable cause = failure != null && failure.getCause() != null ? failure.getCause() : failure;
                status = cause instanceof TimeoutException ? STATUS_GATEWAY_TIMEOUT : STATUS_BAD_GATEWAY;
                body = EMPTY;
                CompletableFuture<Fetched> pending = fetch;
                if (pending != null) {
                    pending.cancel(true);
                }
                DEBUG_LOGGER.debug("Proxied asset failed url={} status={} reason={}", upstreamUrl, status, cause);
                return;
            }

            int localStatus = localStatusFor(fetched.status());
            boolean complete = localStatus != STATUS_OK || fetched.body() != null;
            status = complete ? localStatus : STATUS_BAD_GATEWAY;
            body = localStatus == STATUS_OK && fetched.body() != null ? fetched.body() : EMPTY;
        } finally {
            releaseSlot();
        }
    }

    private void releaseSlot() {
        if (slotHeld.compareAndSet(true, false)) {
            slots.release();
        }
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
        // A deadline, a cancel from CEF or any failure ends the exchange too, so no stream stays open.
        fetched.whenComplete((ignoredResult, failure) -> {
            if (failure != null) {
                send.cancel(true);
            }
        });
        return fetched;
    }

    /**
     * Reads a 200 body up to {@link #MAX_BODY_BYTES} (a larger one gives {@code null}); any other status is
     * discarded unread.
     */
    static HttpResponse.BodySubscriber<byte[]> cappedBody(HttpResponse.ResponseInfo responseInfo) {
        if (responseInfo.statusCode() != STATUS_OK) {
            return HttpResponse.BodySubscribers.replacing(null);
        }

        long declaredLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);
        if (declaredLength > MAX_BODY_BYTES) {
            return HttpResponse.BodySubscribers.replacing(null);
        }

        return new CappedBodySubscriber(MAX_BODY_BYTES);
    }

    record Fetched(int status, byte[] body) {
    }

    /**
     * Collects a body without blocking, and gives {@code null} as soon as it grows past its cap.
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
