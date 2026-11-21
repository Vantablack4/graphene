package tytoo.grapheneui.internal.cef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import tytoo.grapheneui.internal.bridge.GrapheneBridgeRuntime;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.mc.McClient;
import tytoo.grapheneui.internal.url.GrapheneBundledPages;

import java.util.Objects;

final class GrapheneCefRequestHandler extends CefRequestHandlerAdapter {
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneCefRequestHandler.class);

    private final GrapheneBundledPages bundledPages;
    private final GrapheneBridgeRuntime bridgeRuntime;

    GrapheneCefRequestHandler() {
        this(GrapheneBundledPages.empty(), null);
    }

    GrapheneCefRequestHandler(GrapheneBundledPages bundledPages, GrapheneBridgeRuntime bridgeRuntime) {
        this.bundledPages = Objects.requireNonNull(bundledPages, "bundledPages");
        this.bridgeRuntime = bridgeRuntime;
    }

    static boolean redirectsTabOpenIntoBrowser(boolean fromMainFrame, String targetUrl) {
        return fromMainFrame && targetUrl != null && !targetUrl.isBlank();
    }

    @Override
    public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl, boolean userGesture) {
        if (browser == null || !redirectsTabOpenIntoBrowser(frame != null && frame.isMain(), targetUrl)) {
            DEBUG_LOGGER.debug("Blocked tab-open navigation targetUrl={} userGesture={}", targetUrl, userGesture);
            return true;
        }

        McClient.runOnMainThread(() -> browser.loadURL(targetUrl));
        DEBUG_LOGGER.debug("Redirected tab-open navigation into current browser targetUrl={} userGesture={}", targetUrl, userGesture);
        return true;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(
            CefBrowser browser,
            CefFrame frame,
            CefRequest request,
            boolean isNavigation,
            boolean isDownload,
            String requestInitiator,
            BoolRef disableDefaultHandling
    ) {
        if (request == null) {
            return null;
        }

        String url = request.getURL();
        GrapheneBundledPages.Resolution resolution = bundledPages.resolve(url);
        boolean mainFrameDocument = isNavigation
                && (frame == null || frame.isMain())
                && request.getResourceType() == CefRequest.ResourceType.RT_MAIN_FRAME;
        if (mainFrameDocument && bridgeRuntime != null && browser != null) {
            bridgeRuntime.onMainFrameRequest(browser, resolution.resourcePath() == null ? null : url);
        }

        if (!resolution.mounted()) {
            return null;
        }

        DEBUG_LOGGER.debug("Serving bundled page resource url={} resource={}", url, resolution.resourcePath());
        return new BundledPageRequestHandler(resolution);
    }

    static CefResourceHandler bundledResponse(GrapheneBundledPages.Resolution resolution) {
        return resolution.refused()
                ? GrapheneClasspathSchemeHandlerFactory.refusedResource()
                : GrapheneClasspathSchemeHandlerFactory.bundledResource(resolution.resourcePath());
    }

    private static final class BundledPageRequestHandler extends CefResourceRequestHandlerAdapter {
        private final GrapheneBundledPages.Resolution resolution;

        private BundledPageRequestHandler(GrapheneBundledPages.Resolution resolution) {
            this.resolution = resolution;
        }

        @Override
        public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
            return bundledResponse(resolution);
        }
    }
}
