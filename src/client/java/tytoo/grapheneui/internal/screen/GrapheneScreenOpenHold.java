package tytoo.grapheneui.internal.screen;

import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Decides whether a screen opened over the world keeps the world sharp while its page has not painted yet. The world
 * then blurs on the same frame the page appears, so the player never sees a blurred world with nothing on it.
 */
public final class GrapheneScreenOpenHold {
    static final long LIMIT_NANOS = TimeUnit.MILLISECONDS.toNanos(800);

    private GrapheneScreenOpenHold() {
    }

    public static boolean holdsBackground(long elapsedNanos, List<GrapheneWebViewWidget> webViews) {
        List<WebViewState> states = new ArrayList<>(webViews.size());
        for (GrapheneWebViewWidget webView : webViews) {
            states.add(stateOf(webView.getSurface()));
        }

        return holdsBackgroundForStates(elapsedNanos, states);
    }

    static boolean holdsBackgroundForStates(long elapsedNanos, List<WebViewState> webViews) {
        return elapsedNanos < LIMIT_NANOS
                && webViews.contains(WebViewState.BLANK)
                && !webViews.contains(WebViewState.UNAVAILABLE);
    }

    private static WebViewState stateOf(BrowserSurface surface) {
        if (surface.isStarting() || surface.hasFailed()) {
            return WebViewState.UNAVAILABLE;
        }

        return surface.hasVisibleContent() ? WebViewState.PAINTED : WebViewState.BLANK;
    }

    enum WebViewState {
        BLANK,
        PAINTED,
        UNAVAILABLE
    }
}
