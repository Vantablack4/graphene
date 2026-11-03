package tytoo.grapheneuitest;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.api.bridge.GrapheneBridgeSubscription;
import tytoo.grapheneui.api.surface.BrowserSurfaceConfig;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;
import tytoo.grapheneui.internal.browser.GrapheneOpenTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Opens a page the way mod-characters' CharacterCardScreen does: one full-screen web view at 2 CSS px per GUI pixel,
 * its init payload answered and emitted over the bridge, and the web view closed when the screen goes away.
 */
final class OpenTimingScreen extends Screen {
    private static final String INIT_CHANNEL = "vb:character-card:init";
    private static final double CSS_PIXELS_PER_GUI_PIXEL = 2.0D;

    private final String pagePath;
    private final String cardJson;
    private final List<GrapheneBridgeSubscription> subscriptions = new ArrayList<>();
    private GrapheneWebViewWidget webView;

    OpenTimingScreen(String pagePath, String cardJson) {
        super(Component.literal("Open timing"));
        this.pagePath = pagePath;
        this.cardJson = cardJson;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (webView == null) {
            GrapheneHandle graphene = GrapheneCore.handle(GrapheneGameTestClient.class);
            webView = new GrapheneWebViewWidget(
                    this,
                    0,
                    0,
                    Math.max(1, width),
                    Math.max(1, height),
                    Component.empty(),
                    graphene.appAssets().asset(pagePath),
                    surfaceConfig()
            );
            subscriptions.add(webView.bridge().onReady(() -> webView.bridge().emit(INIT_CHANNEL, cardJson)));
            subscriptions.add(webView.bridge().onRequest(
                    INIT_CHANNEL,
                    (ignoredChannel, ignoredPayload) -> CompletableFuture.completedFuture(cardJson)
            ));
        } else {
            webView.setPosition(0, 0);
            webView.setSize(Math.max(1, width), Math.max(1, height));
        }

        addRenderableWidget(webView);
        webView.requestKeyboardFocus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        GrapheneOpenTrace.mark(null, "screen.frame", "");
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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

    GrapheneWebViewWidget webView() {
        return webView;
    }

    private static BrowserSurfaceConfig surfaceConfig() {
        BrowserSurfaceConfig.Builder config = BrowserSurfaceConfig.builder().cssPixelsPerSurfacePixel(CSS_PIXELS_PER_GUI_PIXEL);
        int maxFps = Integer.getInteger("graphene.openTiming.maxFps", 0);
        if (maxFps > 0) {
            config.maxFps(maxFps);
        }

        return config.build();
    }
}
