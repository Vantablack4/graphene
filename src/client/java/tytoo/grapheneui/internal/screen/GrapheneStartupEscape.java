package tytoo.grapheneui.internal.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;

import java.util.List;

public final class GrapheneStartupEscape {
    private GrapheneStartupEscape() {
    }

    public static boolean closeIfStarting(Screen screen, KeyEvent event) {
        if (!event.isEscape() || !screen.shouldCloseOnEsc() || !(screen instanceof GrapheneScreenBridge screenBridge)) {
            return false;
        }

        List<GrapheneWebViewWidget> webViews = screenBridge.graphene$webViewWidgets();
        if (webViews.isEmpty()) {
            return false;
        }

        for (GrapheneWebViewWidget webView : webViews) {
            if (!webView.isStarting() && !webView.hasFailed()) {
                return false;
            }
        }

        screen.onClose();
        return true;
    }
}
