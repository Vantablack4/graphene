package tytoo.grapheneui.internal.bridge;

import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBridgeCallerEndpointTest {
    private static final String PAGE = "app://assets/mod_manuscript/web/manuscript/index.html?mode=editor";
    private static final String CHANNEL = "vb:manuscript:action";
    private static final String SAVE_REQUEST = """
            {"bridge":"graphene-ui","version":1,"kind":"request","id":"js-1","channel":"vb:manuscript:action","payload":{"type":"saveBook"}}
            """;
    private static final String SAVE_EVENT = """
            {"bridge":"graphene-ui","version":1,"kind":"event","channel":"vb:manuscript:action","payload":{"type":"savePage"}}
            """;
    private static final String READY = """
            {"bridge":"graphene-ui","version":1,"kind":"ready"}
            """;
    private static final String UNKNOWN_CHANNEL_REQUEST = """
            {"bridge":"graphene-ui","version":1,"kind":"request","id":"js-2","channel":"vb:unknown","payload":null}
            """;
    private static final String REFUSED = "failure 403";
    private static final String BUNDLED_PAGE = "https://cdn.vantablack4.com/webassets/manuscript/bundled/index.html?mode=editor";
    private static final String NETWORK_PAGE = "https://cdn.vantablack4.com/webassets/manuscript/game-assets/evil.html";

    @Test
    void aSubframeOfThePageCannotCallTheBridge() {
        GrapheneBridgeEndpoint endpoint = endpoint();
        AtomicInteger handled = new AtomicInteger();
        endpoint.onRequest(CHANNEL, (ignoredChannel, ignoredPayload) -> {
            handled.incrementAndGet();
            return CompletableFuture.completedFuture("{}");
        });
        endpoint.onEvent(CHANNEL, (ignoredChannel, ignoredPayload) -> handled.incrementAndGet());

        for (String message : List.of(SAVE_REQUEST, SAVE_EVENT, READY)) {
            RecordingCallback callback = new RecordingCallback();
            assertTrue(endpoint.handleQuery(message, callback, false, PAGE, false));
            assertEquals(List.of(REFUSED), callback.results, message);
        }

        assertEquals(0, handled.get());
        assertFalse(endpoint.isReady());
        endpoint.close();
    }

    @Test
    void framesAndPopupsFromAnotherOriginCannotCallTheBridge() {
        GrapheneBridgeEndpoint endpoint = endpoint();
        AtomicInteger handled = new AtomicInteger();
        endpoint.onRequest(CHANNEL, (ignoredChannel, ignoredPayload) -> {
            handled.incrementAndGet();
            return CompletableFuture.completedFuture("{}");
        });

        RecordingCallback subframe = new RecordingCallback();
        endpoint.handleQuery(SAVE_REQUEST, subframe, false, "https://evil.example/frame.html", false);
        RecordingCallback popupInMainFrame = new RecordingCallback();
        endpoint.handleQuery(SAVE_REQUEST, popupInMainFrame, true, "https://evil.example/popup.html", false);
        RecordingCallback blankPopup = new RecordingCallback();
        endpoint.handleQuery(SAVE_REQUEST, blankPopup, true, "about:blank", false);

        assertEquals(List.of(REFUSED), subframe.results);
        assertEquals(List.of(REFUSED), popupInMainFrame.results);
        assertEquals(List.of(REFUSED), blankPopup.results);
        assertEquals(0, handled.get());
        endpoint.close();
    }

    @Test
    void theMainFrameOfTheLoadedPageReachesTheRouter() {
        GrapheneBridgeEndpoint endpoint = endpoint();
        RecordingCallback callback = new RecordingCallback();

        assertTrue(endpoint.handleQuery(UNKNOWN_CHANNEL_REQUEST, callback, true, PAGE, false));

        assertEquals(1, callback.results.size());
        String result = callback.results.getFirst();
        assertTrue(result.startsWith("success ") && result.contains("handler_not_found"), result);
        endpoint.close();
    }

    @Test
    void onABundledOriginANetworkDocumentCannotCallTheBridge() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        endpoint.trustPage(BUNDLED_PAGE);
        AtomicInteger handled = new AtomicInteger();
        endpoint.onEvent(CHANNEL, (ignoredChannel, ignoredPayload) -> handled.incrementAndGet());

        RecordingCallback beforeAnyDocument = new RecordingCallback();
        endpoint.handleQuery(SAVE_EVENT, beforeAnyDocument, true, BUNDLED_PAGE, true);
        endpoint.onMainFrameCommit(NETWORK_PAGE);
        RecordingCallback networkDocument = new RecordingCallback();
        endpoint.handleQuery(SAVE_EVENT, networkDocument, true, NETWORK_PAGE, true);
        endpoint.onMainFrameRequest(BUNDLED_PAGE);
        endpoint.onMainFrameCommit(NETWORK_PAGE);
        RecordingCallback otherDocumentCommitted = new RecordingCallback();
        endpoint.handleQuery(SAVE_EVENT, otherDocumentCommitted, true, NETWORK_PAGE, true);

        assertEquals(List.of(REFUSED), beforeAnyDocument.results);
        assertEquals(List.of(REFUSED), networkDocument.results);
        assertEquals(List.of(REFUSED), otherDocumentCommitted.results);
        assertEquals(0, handled.get());
        endpoint.close();
    }

    @Test
    void onABundledOriginTheDocumentServedFromTheJarReachesTheRouterUntilTheNextDocument() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        endpoint.trustPage(BUNDLED_PAGE);

        endpoint.onMainFrameRequest(BUNDLED_PAGE);
        endpoint.onMainFrameCommit(BUNDLED_PAGE + "#editor");
        RecordingCallback jarDocument = new RecordingCallback();
        endpoint.handleQuery(UNKNOWN_CHANNEL_REQUEST, jarDocument, true, BUNDLED_PAGE + "#editor", true);
        endpoint.onMainFrameRequest(null);
        endpoint.onMainFrameCommit(NETWORK_PAGE);
        RecordingCallback nextDocument = new RecordingCallback();
        endpoint.handleQuery(UNKNOWN_CHANNEL_REQUEST, nextDocument, true, NETWORK_PAGE, true);

        assertTrue(jarDocument.results.getFirst().contains("handler_not_found"), jarDocument.results.toString());
        assertEquals(List.of(REFUSED), nextDocument.results);
        endpoint.close();
    }

    private static GrapheneBridgeEndpoint endpoint() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        endpoint.trustPage(PAGE);
        return endpoint;
    }

    private static final class RecordingCallback implements CefQueryCallback {
        private final List<String> results = new ArrayList<>();

        @Override
        public void success(String response) {
            results.add("success " + response);
        }

        @Override
        public void failure(int errorCode, String errorMessage) {
            results.add("failure " + errorCode);
        }
    }
}
