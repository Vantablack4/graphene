package tytoo.grapheneui.internal.cef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import tytoo.grapheneui.internal.browser.GrapheneOpenTrace;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;

final class GrapheneCefLifeSpanHandler extends CefLifeSpanHandlerAdapter {
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneCefLifeSpanHandler.class);

    @Override
    public void onAfterCreated(CefBrowser browser) {
        GrapheneOpenTrace.mark(browser, "browser.created", "");
    }

    @Override
    public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
        // A popup would be a new browser on this client, and loading it into this one would hand the opener's frame
        // the surface. Pages navigate themselves instead; bridge.js turns the main frame's window.open into that.
        DEBUG_LOGGER.debug(
                "Blocked popup mainFrame={} targetUrl={} targetFrame={}",
                frame != null && frame.isMain(),
                targetUrl,
                targetFrameName
        );
        return true;
    }
}
