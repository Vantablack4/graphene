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
 * Stands in for the network on the owned test host: a request Graphene failed to answer would come here. It records
 * every request, and the test expects none.
 */
final class NetworkStandInSchemeHandlerFactory implements CefSchemeHandlerFactory {
    static final String HOST = "graphene-bundled.invalid";
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

    @Override
    public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
        requestedPaths.add(URI.create(request.getURL()).getRawPath());
        return new StaticResponse("text/html", "<script>cefQuery({})</script>");
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
