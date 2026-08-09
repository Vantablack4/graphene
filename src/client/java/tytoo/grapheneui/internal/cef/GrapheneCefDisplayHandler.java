package tytoo.grapheneui.internal.cef;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import tytoo.grapheneui.internal.browser.GrapheneBrowser;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;

final class GrapheneCefDisplayHandler extends CefDisplayHandlerAdapter {
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneCefDisplayHandler.class);

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        if (browser instanceof GrapheneBrowser grapheneBrowser) {
            grapheneBrowser.onTitleChange(title);
        }
    }

    @Override
    public boolean onConsoleMessage(
            CefBrowser browser,
            CefSettings.LogSeverity level,
            String message,
            String source,
            int line
    ) {
        DEBUG_LOGGER.debug(
                "CEF console browserId={} level={} source={}:{} message={}",
                browserIdentifier(browser),
                level,
                source,
                line,
                message
        );
        return false;
    }

    private static int browserIdentifier(CefBrowser browser) {
        try {
            return browser.getIdentifier();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }
}
