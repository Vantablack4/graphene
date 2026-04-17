package tytoo.grapheneui.internal.cef;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import tytoo.grapheneui.internal.browser.GrapheneBrowser;
import tytoo.grapheneui.internal.event.GrapheneTitleEventBus;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Objects;

final class GrapheneCefDisplayHandler extends CefDisplayHandlerAdapter {
    private final GrapheneTitleEventBus titleEventBus;

    GrapheneCefDisplayHandler(GrapheneTitleEventBus titleEventBus) {
        this.titleEventBus = Objects.requireNonNull(titleEventBus, "titleEventBus");
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        if (!(browser instanceof GrapheneBrowser grapheneBrowser)) {
            return;
        }

        McClient.runOnMainThread(() -> {
            if (!grapheneBrowser.updateTitle(title)) {
                return;
            }

            titleEventBus.onTitleChange(grapheneBrowser, grapheneBrowser.currentTitle());
        });
    }
}
