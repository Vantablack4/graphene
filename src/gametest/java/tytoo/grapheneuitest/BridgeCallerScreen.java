package tytoo.grapheneuitest;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import tytoo.grapheneui.api.bridge.GrapheneBridgeSubscription;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One full-screen web view that counts every bridge call Java receives, by channel, and keeps the last payload.
 */
final class BridgeCallerScreen extends Screen {
    private static final List<String> EVENT_CHANNELS = List.of("test:report", "test:frame", "test:popup", "test:bundled");
    private static final String REQUEST_CHANNEL = "test:main";

    private final String url;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Map<String, String> payloads = new ConcurrentHashMap<>();
    private final List<GrapheneBridgeSubscription> subscriptions = new ArrayList<>();
    private GrapheneWebViewWidget webView;

    BridgeCallerScreen(String url) {
        super(Component.literal("Bridge callers"));
        this.url = url;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (webView == null) {
            webView = new GrapheneWebViewWidget(this, 0, 0, Math.max(1, width), Math.max(1, height), Component.empty(), url);
            for (String channel : EVENT_CHANNELS) {
                subscriptions.add(webView.bridge().onEvent(channel, (receivedChannel, payload) -> record(receivedChannel, payload)));
            }
            subscriptions.add(webView.bridge().onRequest(REQUEST_CHANNEL, (receivedChannel, payload) -> {
                record(receivedChannel, payload);
                return CompletableFuture.completedFuture("{}");
            }));
        } else {
            webView.setPosition(0, 0);
            webView.setSize(Math.max(1, width), Math.max(1, height));
        }

        addRenderableWidget(webView);
    }

    @Override
    public void removed() {
        subscriptions.forEach(GrapheneBridgeSubscription::unsubscribe);
        subscriptions.clear();
        if (webView != null) {
            webView.close();
            webView = null;
        }

        super.removed();
    }

    int calls(String channel) {
        AtomicInteger count = calls.get(channel);
        return count == null ? 0 : count.get();
    }

    String payload(String channel) {
        return payloads.get(channel);
    }

    String currentUrl() {
        return webView == null ? null : webView.currentUrl();
    }

    private void record(String channel, String payload) {
        calls.computeIfAbsent(channel, ignoredChannel -> new AtomicInteger()).incrementAndGet();
        payloads.put(channel, payload == null ? "null" : payload);
    }
}
