package tytoo.grapheneuidebug.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.bridge.GrapheneBridgeSubscription;
import tytoo.grapheneui.api.surface.BrowserSurfaceAccelerationStatus;
import tytoo.grapheneui.api.surface.BrowserSurfaceConfig;
import tytoo.grapheneui.api.surface.BrowserSurfaceFrameScheduling;
import tytoo.grapheneui.api.surface.BrowserSurfacePerformanceSnapshot;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;
import tytoo.grapheneuidebug.GrapheneDebugClient;
import tytoo.grapheneuidebug.test.GrapheneDebugTestRunner;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class GrapheneBrowserDebugScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneBrowserDebugScreen.class);

    private static final String DEBUG_EVENT_CHANNEL = "debug:event";
    private static final String DEBUG_ECHO_CHANNEL = "debug:echo";
    private static final String DEBUG_SUM_CHANNEL = "debug:sum";
    private static final String DEBUG_DEVTOOLS_STATUS_CHANNEL = "debug:devtools-status";
    private static final String DEBUG_TESTS_RUN_CHANNEL = "debug:tests:run";
    private static final String DEBUG_PERFORMANCE_SNAPSHOT_CHANNEL = "debug:performance:snapshot";
    private static final String DEBUG_JAVA_TO_JS_TRIGGER_CHANNEL = "debug:bridge:trigger-java-to-js";
    private static final String DEBUG_JAVA_TO_JS_EVENT_CHANNEL = "debug:bridge:java-event";
    private static final String DEBUG_JAVA_TO_JS_REQUEST_CHANNEL = "debug:bridge:java-request";
    private static final Duration DEBUG_JAVA_TO_JS_REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private static String lastUrl;
    private final List<GrapheneBridgeSubscription> bridgeSubscriptions = new ArrayList<>();
    private GrapheneWebViewWidget webViewWidget;
    private EditBox urlBox;
    private Button backButton;
    private Button forwardButton;

    public GrapheneBrowserDebugScreen() {
        super(Component.translatable("screen.graphene-ui-debug.title"));
    }

    private static String defaultUrl() {
        return GrapheneCore.handle(GrapheneDebugClient.class)
                .appAssets()
                .asset("graphene_test/pages/welcome.html");
    }

    private static String initialUrl() {
        return lastUrl == null ? defaultUrl() : lastUrl;
    }

    private static void rememberLastUrl(String url) {
        lastUrl = url;
    }

    private static String buildEchoResponse(String payloadJson) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("kind", "echo");
        response.addProperty("receivedAt", Instant.now().toString());
        response.add("received", parsePayload(payloadJson));
        return response.toString();
    }

    private static String buildSumResponse(String payloadJson) {
        JsonObject response = new JsonObject();
        response.addProperty("kind", "sum");
        response.addProperty("receivedAt", Instant.now().toString());

        try {
            JsonObject payload = parsePayload(payloadJson).getAsJsonObject();
            double left = payload.get("a").getAsDouble();
            double right = payload.get("b").getAsDouble();
            response.addProperty("ok", true);
            response.addProperty("a", left);
            response.addProperty("b", right);
            response.addProperty("result", left + right);
            return response.toString();
        } catch (RuntimeException ignored) {
            response.addProperty("ok", false);
            response.addProperty("error", "Payload must be a JSON object with numeric fields 'a' and 'b'.");
            return response.toString();
        }
    }

    private static JsonElement parsePayload(String payloadJson) {
        String value = payloadJson == null ? "null" : payloadJson;
        try {
            return JsonParser.parseString(value);
        } catch (RuntimeException ignored) {
            // Ignore malformed payloads and treat them as null in debug helpers.
            return JsonNull.INSTANCE;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }

        return throwable;
    }

    private CompletableFuture<String> runJavaToJsRoundTrip(String payloadJson) {
        if (webViewWidget == null) {
            JsonObject response = new JsonObject();
            response.addProperty("ok", false);
            response.addProperty("error", "Web view is unavailable");
            return CompletableFuture.completedFuture(response.toString());
        }

        GrapheneBridge bridge = webViewWidget.bridge();

        JsonObject eventPayload = new JsonObject();
        eventPayload.addProperty("kind", "java-event");
        eventPayload.addProperty("sentAt", Instant.now().toString());
        eventPayload.add("fromJsPayload", parsePayload(payloadJson));
        bridge.emit(DEBUG_JAVA_TO_JS_EVENT_CHANNEL, eventPayload.toString());

        JsonObject requestPayload = new JsonObject();
        requestPayload.addProperty("kind", "java-request");
        requestPayload.addProperty("sentAt", Instant.now().toString());
        requestPayload.add("fromJsPayload", parsePayload(payloadJson));

        return bridge.request(
                        DEBUG_JAVA_TO_JS_REQUEST_CHANNEL,
                        requestPayload.toString(),
                        DEBUG_JAVA_TO_JS_REQUEST_TIMEOUT
                )
                .thenApply(responsePayloadJson -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("ok", true);
                    response.addProperty("kind", "java-to-js-roundtrip");
                    response.addProperty("completedAt", Instant.now().toString());
                    response.add("requestResponse", parsePayload(responsePayloadJson));
                    return response.toString();
                })
                .exceptionally(throwable -> {
                    Throwable rootCause = unwrap(throwable);
                    JsonObject response = new JsonObject();
                    response.addProperty("ok", false);
                    response.addProperty("kind", "java-to-js-roundtrip");
                    response.addProperty("errorType", rootCause.getClass().getSimpleName());
                    response.addProperty(
                            "errorMessage",
                            rootCause.getMessage() == null ? "No error message provided" : rootCause.getMessage()
                    );
                    return response.toString();
                });
    }

    private String buildPerformanceSnapshot() {
        JsonObject response = new JsonObject();
        if (webViewWidget == null) {
            response.addProperty("ok", false);
            response.addProperty("error", "Web view is unavailable");
            return response.toString();
        }

        BrowserSurfaceAccelerationStatus acceleration = webViewWidget.getSurface().accelerationStatus();
        response.addProperty("sharedTextureRequested", acceleration.sharedTextureRequested());
        response.addProperty("sharedTextureActive", acceleration.sharedTextureActive());
        response.addProperty("activePath", acceleration.activePath());
        response.addProperty("sharedTextureUnavailableReason", acceleration.sharedTextureUnavailableReason());

        BrowserSurfacePerformanceSnapshot snapshot = webViewWidget.getSurface().performanceSnapshot().orElse(null);
        if (snapshot == null) {
            response.addProperty("ok", false);
            response.addProperty("error", "Performance metrics are disabled for this surface");
            return response.toString();
        }

        response.addProperty("ok", true);
        response.addProperty("paintFrames", snapshot.paintFrames());
        response.addProperty("fullFrameCopies", snapshot.fullFrameCopies());
        response.addProperty("partialFrameCopies", snapshot.partialFrameCopies());
        response.addProperty("capturedBytes", snapshot.capturedBytes());
        response.addProperty("uploadedFrames", snapshot.uploadedFrames());
        response.addProperty("fullFrameUploads", snapshot.fullFrameUploads());
        response.addProperty("partialFrameUploads", snapshot.partialFrameUploads());
        response.addProperty("uploadedBytes", snapshot.uploadedBytes());
        response.addProperty("coalescedPaintFrames", snapshot.coalescedPaintFrames());
        response.addProperty("dirtyHistoryFallbacks", snapshot.dirtyHistoryFallbacks());
        response.addProperty("captureNanos", snapshot.captureNanos());
        response.addProperty("uploadNanos", snapshot.uploadNanos());
        response.addProperty("externalBeginFrames", snapshot.externalBeginFrames());
        return response.toString();
    }

    private void openRemoteDevTools() {
        int debugPort = GrapheneCore.runtime().getRemoteDebuggingPort();
        if (debugPort > 0) {
            Util.getPlatform().openUri(URI.create("http://127.0.0.1:" + debugPort + "/json"));
            emitDevToolsStatus(true, debugPort);
            return;
        }

        emitDevToolsStatus(false, debugPort);
    }

    @Override
    protected void init() {
        clearWidgets();
        String initialUrl = initialUrl();

        int controlsY = 8;
        int controlHeight = 20;
        int webViewY = controlsY + controlHeight + 8;
        int webViewWidth = width - 16;
        int webViewHeight = height - webViewY - 8;

        if (webViewWidget == null) {
            BrowserSurfaceConfig surfaceConfig = BrowserSurfaceConfig.builder()
                    .frameScheduling(BrowserSurfaceFrameScheduling.RENDER_DRIVEN)
                    .performanceMetrics(true)
                    .build();
            webViewWidget = new GrapheneWebViewWidget(
                    this,
                    8,
                    webViewY,
                    webViewWidth,
                    webViewHeight,
                    Component.empty(),
                    initialUrl,
                    surfaceConfig
            );
        } else {
            webViewWidget.setPosition(8, webViewY);
            webViewWidget.setSize(webViewWidth, webViewHeight);
        }

        configureBridgeHandlers();

        addRenderableWidget(webViewWidget);

        backButton = addRenderableWidget(
                Button.builder(Component.translatable("screen.graphene-ui-debug.back"), ignoredButton -> webViewWidget.goBack())
                        .bounds(8, controlsY, 26, controlHeight)
                        .build()
        );

        addRenderableWidget(
                Button.builder(Component.translatable("screen.graphene-ui-debug.reload"), ignoredButton -> webViewWidget.reload())
                        .bounds(38, controlsY, 26, controlHeight)
                        .build()
        );

        forwardButton = addRenderableWidget(
                Button.builder(Component.translatable("screen.graphene-ui-debug.forward"), ignoredButton -> webViewWidget.goForward())
                        .bounds(68, controlsY, 26, controlHeight)
                        .build()
        );

        addRenderableWidget(
                Button.builder(Component.translatable("screen.graphene-ui-debug.devtools"), ignoredButton -> openRemoteDevTools())
                        .bounds(98, controlsY, 66, controlHeight)
                        .build()
        );

        urlBox = addRenderableWidget(new EditBox(font, 168, controlsY, width - 176, controlHeight, Component.empty()));
        urlBox.setMaxLength(Integer.MAX_VALUE);
        urlBox.setValue(initialUrl);

        webViewWidget.requestKeyboardFocus();
    }

    @Override
    public void tick() {
        if (webViewWidget == null) {
            return;
        }

        if (urlBox != null && !urlBox.isFocused()) {
            urlBox.setValue(webViewWidget.currentUrl());
        }

        if (backButton != null) {
            backButton.active = webViewWidget.canGoBack();
        }

        if (forwardButton != null) {
            forwardButton.active = webViewWidget.canGoForward();
        }
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent keyEvent) {
        if (urlBox != null && urlBox.isFocused() && keyEvent.isConfirmation()) {
            webViewWidget.loadUrl(urlBox.getValue());
            return true;
        }

        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        if (webViewWidget != null) {
            rememberLastUrl(webViewWidget.currentUrl());
        }

        clearBridgeSubscriptions();

        super.onClose();
    }

    private void configureBridgeHandlers() {
        clearBridgeSubscriptions();

        GrapheneBridge bridge = webViewWidget.bridge();
        bridgeSubscriptions.add(bridge.onEvent(DEBUG_EVENT_CHANNEL, (channel, payloadJson) ->
                LOGGER.info("Received bridge event on {}: {}", channel, payloadJson)
        ));
        bridgeSubscriptions.add(bridge.onRequest(DEBUG_ECHO_CHANNEL, (ignoredChannel, payloadJson) ->
                CompletableFuture.completedFuture(buildEchoResponse(payloadJson))
        ));
        bridgeSubscriptions.add(bridge.onRequest(DEBUG_SUM_CHANNEL, (ignoredChannel, payloadJson) ->
                CompletableFuture.completedFuture(buildSumResponse(payloadJson))
        ));
        bridgeSubscriptions.add(bridge.onRequest(DEBUG_TESTS_RUN_CHANNEL, (ignoredChannel, ignoredPayloadJson) ->
                GrapheneDebugTestRunner.runAllTestsAsJson()
        ));
        bridgeSubscriptions.add(bridge.onRequest(DEBUG_PERFORMANCE_SNAPSHOT_CHANNEL, (ignoredChannel, ignoredPayloadJson) ->
                CompletableFuture.completedFuture(buildPerformanceSnapshot())
        ));
        bridgeSubscriptions.add(bridge.onRequest(DEBUG_JAVA_TO_JS_TRIGGER_CHANNEL, (ignoredChannel, payloadJson) ->
                runJavaToJsRoundTrip(payloadJson)
        ));
    }

    private void clearBridgeSubscriptions() {
        for (GrapheneBridgeSubscription bridgeSubscription : bridgeSubscriptions) {
            bridgeSubscription.unsubscribe();
        }

        bridgeSubscriptions.clear();
    }

    private void emitDevToolsStatus(boolean opened, int debugPort) {
        if (webViewWidget == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("opened", opened);
        payload.addProperty("debugPort", debugPort);
        payload.addProperty("openedAt", Instant.now().toString());
        webViewWidget.bridge().emit(DEBUG_DEVTOOLS_STATUS_CHANNEL, payload.toString());
    }
}
