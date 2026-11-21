package tytoo.grapheneuitest;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the network on the bundled test host: whatever Graphene does not answer from the jar comes here, as
 * it would come from the CDN. It serves a same-origin document that tries the bridge, and records every request.
 */
final class NetworkStandInSchemeHandlerFactory implements CefSchemeHandlerFactory {
    static final String HOST = "graphene-bundled.invalid";
    static final String NETWORK_PAGE_PATH = "/outside/network.html";
    private static final String NETWORK_PAGE = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="utf-8"><title>Network page</title></head><body>
            <script>
              window.cefQuery({
                request: JSON.stringify({ bridge: "graphene-ui", version: 1, kind: "event", channel: "test:outside", payload: {} }),
                onSuccess: function () { location.assign("/page/index.html?step=back&outside=ok"); },
                onFailure: function (code) { location.assign("/page/index.html?step=back&outside=" + code); }
              });
            </script>
            </body></html>
            """;

    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

    @Override
    public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
        String path = URI.create(request.getURL()).getRawPath();
        requestedPaths.add(path);
        boolean page = NETWORK_PAGE_PATH.equals(path);
        return new StaticResponse(page ? "text/html" : "text/plain", page ? NETWORK_PAGE : "from the network");
    }

    List<String> requestedPaths() {
        return requestedPaths;
    }

    private static final class StaticResponse extends CefResourceHandlerAdapter {
        private final String mimeType;
        private final byte[] body;
        private int offset;

        private StaticResponse(String mimeType, String body) {
            this.mimeType = mimeType;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(200);
            response.setMimeType(mimeType);
            responseLength.set(body.length);
        }

        @Override
        public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            int remaining = body.length - offset;
            if (remaining <= 0) {
                bytesRead.set(0);
                return false;
            }

            int count = Math.min(bytesToRead, remaining);
            System.arraycopy(body, offset, dataOut, 0, count);
            offset += count;
            bytesRead.set(count);
            return true;
        }
    }
}
