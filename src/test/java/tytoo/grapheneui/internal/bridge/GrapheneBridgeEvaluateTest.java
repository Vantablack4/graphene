package tytoo.grapheneui.internal.bridge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.bridge.GrapheneBridgeRequestException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBridgeEvaluateTest {
    private static final Duration TIMEOUT = Duration.ofMinutes(1);
    private static final Pattern REQUEST_ID = Pattern.compile("__grapheneBridgeEvaluate\\(\"([^\"]+)\"");

    @Test
    void evaluationWrapsTheScriptAsAnAsyncFunctionBody() {
        List<String> scripts = new ArrayList<>();
        GrapheneBridgeRequestLifecycle lifecycle = lifecycle();

        lifecycle.evaluate("return document.title;", TIMEOUT, scripts::add);

        assertEquals(1, scripts.size());
        String script = scripts.getFirst();
        assertTrue(script.startsWith("window.__grapheneBridgeEvaluate(\"java-eval-"));
        assertTrue(script.contains("\"graphene:evaluate\", async function () {\nreturn document.title;\n});"));
    }

    @Test
    void evaluationCompletesWithTheReturnedValue() throws Exception {
        List<String> scripts = new ArrayList<>();
        GrapheneBridgeRequestLifecycle lifecycle = lifecycle();
        CompletableFuture<String> result = lifecycle.evaluate("return {title: document.title};", TIMEOUT, scripts::add);

        lifecycle.handleResponse(response(requestId(scripts.getFirst()), true, "{\"title\":\"Karakter\"}", null));

        assertEquals(JsonParser.parseString("{\"title\":\"Karakter\"}"), JsonParser.parseString(result.get(1, TimeUnit.SECONDS)));
    }

    @Test
    void evaluationFailsWithTheScriptError() {
        List<String> scripts = new ArrayList<>();
        GrapheneBridgeRequestLifecycle lifecycle = lifecycle();
        CompletableFuture<String> result = lifecycle.evaluate("throw new Error('nope');", TIMEOUT, scripts::add);

        lifecycle.handleResponse(response(requestId(scripts.getFirst()), false, null, "nope"));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        GrapheneBridgeRequestException requestFailure = assertInstanceOf(GrapheneBridgeRequestException.class, failure.getCause());
        assertTrue(requestFailure.getMessage().contains("nope"));
    }

    @Test
    void evaluationFailsWhenTheScriptCannotRun() {
        GrapheneBridgeRequestLifecycle lifecycle = lifecycle();

        CompletableFuture<String> result = lifecycle.evaluate("return 1;", TIMEOUT, ignoredScript -> {
            throw new IllegalStateException("browser gone");
        });

        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void pendingEvaluationFailsWhenThePageChanges() {
        GrapheneBridgeRequestLifecycle lifecycle = lifecycle();
        CompletableFuture<String> result = lifecycle.evaluate("return 1;", TIMEOUT, ignoredScript -> {
        });

        assertFalse(result.isDone());
        lifecycle.failAllForPageChange();

        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void endpointRefusesToEvaluateBeforeTheBridgeIsReady() {
        GrapheneBridgeEndpoint endpoint = new GrapheneBridgeRuntime().createEndpoint();

        CompletableFuture<String> result = endpoint.evaluate("return 1;", TIMEOUT);

        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        endpoint.close();
    }

    private static GrapheneBridgeRequestLifecycle lifecycle() {
        GrapheneBridgeOptions options = GrapheneBridgeOptions.defaults();
        GrapheneBridgeOutboundQueue queue = new GrapheneBridgeOutboundQueue(
                ignoredMessage -> {
                },
                16,
                GrapheneBridgeQueueOverflowPolicy.DROP_OLDEST,
                GrapheneBridgeDiagnostics.noOp()
        );
        return new GrapheneBridgeRequestLifecycle(new GrapheneBridgeMessageCodec(options.gson()), queue);
    }

    private static String requestId(String script) {
        Matcher matcher = REQUEST_ID.matcher(script);
        assertTrue(matcher.find(), "no request id in " + script);
        return matcher.group(1);
    }

    private static GrapheneBridgePacket response(String requestId, boolean ok, String payloadJson, String errorMessage) {
        GrapheneBridgePacket packet = new GrapheneBridgePacket();
        packet.bridge = GrapheneBridgeProtocol.NAME;
        packet.kind = GrapheneBridgeProtocol.KIND_RESPONSE;
        packet.id = requestId;
        packet.channel = GrapheneBridgeProtocol.EVALUATE_CHANNEL;
        packet.ok = ok;
        packet.payload = payloadJson == null ? null : JsonParser.parseString(payloadJson);
        if (errorMessage != null) {
            GrapheneBridgePacketError error = new GrapheneBridgePacketError();
            error.code = "js_handler_error";
            error.message = errorMessage;
            packet.error = error;
        }

        return packet;
    }
}
