package tytoo.grapheneui.internal.bridge;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.bridge.GrapheneBridgeSubscription;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBridgeDeferredEndpointTest {
    @Test
    void unboundEndpointAcceptsHandlersAndQueuesTrafficWithoutABrowser() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        AtomicBoolean readyCalled = new AtomicBoolean();

        GrapheneBridgeSubscription readySubscription = endpoint.onReady(() -> readyCalled.set(true));
        GrapheneBridgeSubscription eventSubscription = endpoint.onEvent("demo:event", (channel, payload) -> {
        });
        endpoint.emit("demo:init", "{\"value\":1}");
        endpoint.emitLatest("demo:state", "state", "{\"value\":2}");
        endpoint.tryBootstrapFallback();
        endpoint.onNavigationRequested();
        endpoint.onPageLoadEnd();

        assertNotNull(readySubscription);
        assertNotNull(eventSubscription);
        assertFalse(endpoint.isReady());
        assertFalse(readyCalled.get());
    }

    @Test
    void requestsMadeBeforeTheFirstPageSurviveItsNavigation() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        CompletableFuture<String> response = endpoint.request("demo:request", "{}", Duration.ofMinutes(1));

        endpoint.onPageLoadStart();
        endpoint.onNavigationRequested();

        assertFalse(response.isDone());
        endpoint.close();
    }

    @Test
    void closingAnUnboundEndpointFailsPendingRequests() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();
        CompletableFuture<String> response = endpoint.request("demo:request", "{}", Duration.ofMinutes(1));

        endpoint.close();

        assertTrue(endpoint.isClosed());
        ExecutionException failure = assertThrows(ExecutionException.class, () -> response.get(1, TimeUnit.SECONDS));
        assertNotNull(failure.getCause());
        assertThrows(IllegalStateException.class, () -> endpoint.emit("demo:init", "{}"));
    }
}
