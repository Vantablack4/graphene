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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Answers one proxied-asset request: a GET of a fixed upstream URL on Graphene's own threads, so the CEF IO thread
 * never waits. Nothing from the page is forwarded (no cookies, headers or query); the type comes from the extension,
 * the body is capped, and any upstream failure is a local error.
 */
final class GrapheneProxiedAssetHandler extends CefResourceHandlerAdapter {
    static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    static final String NOSNIFF = "nosniff";
    static final String SANDBOX = "sandbox";

    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneProxiedAssetHandler.class);
    private static final String METHOD_GET = "GET";
    private static final String TEXT_PLAIN = "text/plain";
    private static final int STATUS_OK = 200;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final byte[] EMPTY = new byte[0];
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "graphene-asset-proxy");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(EXECUTOR)
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String upstreamUrl;
    private final String mimeType;
    private volatile int status = STATUS_BAD_GATEWAY;
    private volatile byte[] body = EMPTY;
    private volatile CompletableFuture<Void> fetch;
    private volatile boolean cancelled;
    private int readOffset;

    GrapheneProxiedAssetHandler(String upstreamUrl) {
        this.upstreamUrl = upstreamUrl;
        this.mimeType = GrapheneMimeTypes.resolve(upstreamUrl);
    }

    static int localStatusFor(int upstreamStatus) {
        if (upstreamStatus == STATUS_OK) {
            return STATUS_OK;
        }

        return upstreamStatus == STATUS_NOT_FOUND ? STATUS_NOT_FOUND : STATUS_BAD_GATEWAY;
    }

    static byte[] readCapped(InputStream input, long declaredLength) throws IOException {
        if (declaredLength > MAX_BODY_BYTES) {
            return null;
        }

        byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
        return bytes.length > MAX_BODY_BYTES ? null : bytes;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        if (!METHOD_GET.equalsIgnoreCase(request.getMethod())) {
            status = STATUS_METHOD_NOT_ALLOWED;
            callback.Continue();
            return true;
        }

        HttpRequest upstreamRequest = HttpRequest.newBuilder(URI.create(upstreamUrl))
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .build();
        fetch = CLIENT.sendAsync(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(this::accept)
                .exceptionally(failure -> {
                    DEBUG_LOGGER.debug("Proxied asset failed url={} reason={}", upstreamUrl, failure.getMessage());
                    status = STATUS_BAD_GATEWAY;
                    body = EMPTY;
                    return null;
                })
                .whenComplete((ignoredResult, ignoredFailure) -> {
                    if (!cancelled) {
                        callback.Continue();
                    }
                });
        return true;
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
        CompletableFuture<Void> pending = fetch;
        if (pending != null) {
            pending.cancel(true);
        }

        body = EMPTY;
    }

    private void accept(HttpResponse<InputStream> response) {
        try (InputStream input = response.body()) {
            int localStatus = localStatusFor(response.statusCode());
            if (localStatus != STATUS_OK) {
                status = localStatus;
                return;
            }

            byte[] bytes = readCapped(input, response.headers().firstValueAsLong("content-length").orElse(-1L));
            if (bytes == null) {
                status = STATUS_BAD_GATEWAY;
                return;
            }

            body = bytes;
            status = STATUS_OK;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
