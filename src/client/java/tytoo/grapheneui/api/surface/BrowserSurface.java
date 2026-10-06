package tytoo.grapheneui.api.surface;

import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRequestContext;
import org.cef.network.CefRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.internal.bridge.GrapheneBridgeEndpoint;
import tytoo.grapheneui.internal.browser.BrowserSurfaceLoadListenerScope;
import tytoo.grapheneui.internal.browser.BrowserSurfaceSizingState;
import tytoo.grapheneui.internal.browser.GrapheneBrowser;
import tytoo.grapheneui.internal.browser.GraphenePageDefaults;
import tytoo.grapheneui.internal.cef.GrapheneCefRuntime;
import tytoo.grapheneui.internal.core.GrapheneCoreServices;
import tytoo.grapheneui.internal.core.GrapheneStartupPolicy;
import tytoo.grapheneui.internal.mc.McWindowScale;
import tytoo.grapheneui.internal.nativeui.GrapheneNativeSlotRegistry;

import java.awt.*;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a browser surface that can be rendered onto Minecraft's GUI.
 * <p>
 * The surface size and resolution can be configured independently, allowing for flexible rendering options.
 * The surface can also have an optional view box to control the portion of the browser content that is rendered.
 * <p>
 * The browser surface provides methods for navigation, loading URLs, and subscribing to load events.
 * It also manages its own lifecycle and should be closed when no longer needed to free resources.
 * <p>
 * A surface built on the render thread while Graphene is still starting (for example while the CEF natives
 * download on first launch) does not wait for it. It stays {@linkplain #isStarting() starting}, renders nothing,
 * queues bridge traffic and remembers the latest URL and size, then creates its browser on the first render or
 * texture request after Graphene is ready. Set the system property {@value #AWAIT_STARTUP_PROPERTY} to
 * {@code true} to restore the blocking behaviour; Fabric client GameTest runs use it by default.
 */

@SuppressWarnings("unused") // Public API
public final class BrowserSurface implements AutoCloseable {
    public static final String AWAIT_STARTUP_PROPERTY = GrapheneStartupPolicy.AWAIT_STARTUP_PROPERTY;
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserSurface.class);
    private static final int MIN_SIZE = 1;
    private static final String OWNER_NAME = "owner";
    private static final String SURFACE_WIDTH_NAME = "surfaceWidth";
    private static final String SURFACE_HEIGHT_NAME = "surfaceHeight";
    private static final Consumer<CefRequestContext> NO_OP_REQUEST_CONTEXT_CUSTOMIZER = ignoredRequestContext -> {
    };

    private final GrapheneBridgeEndpoint bridge;
    private final GrapheneNativeSlotRegistry nativeSlots;
    private final BrowserSurfaceSizingState sizingState;
    private final BrowserSurfaceLoadListenerScope loadListenerScope;
    private final GrapheneCoreServices services;
    private final BrowserSurfaceConfig config;
    private final BrowserSurfaceAccelerationStatus accelerationStatus;
    private final CefClient explicitClient;
    private final CefRequestContext explicitRequestContext;
    private final Consumer<CefRequestContext> requestContextCustomizer;
    private final boolean transparent;
    private volatile GrapheneBrowser browser;
    private String pendingUrl;
    private BrowserSurfaceSizingState.CssViewport appliedCssViewport;
    private boolean activationFailed;
    private boolean closed;

    private BrowserSurface(Builder builder) {
        this.services = GrapheneCoreServices.get();
        this.sizingState = new BrowserSurfaceSizingState(
                builder.surfaceWidth,
                builder.surfaceHeight,
                builder.autoResolution,
                builder.resolutionWidth,
                builder.resolutionHeight,
                builder.viewBox,
                McWindowScale.getScaleX(),
                McWindowScale.getScaleY(),
                builder.config != null && builder.config.usesSurfaceCssPixels()
        );

        this.config = builder.config != null ? builder.config : BrowserSurfaceConfig.defaults();
        this.accelerationStatus = BrowserSurfaceAccelerationStatus.softwareFallback(
                this.config.acceleratedPaintPreferred()
        );
        this.explicitClient = builder.client;
        this.explicitRequestContext = builder.requestContext;
        this.requestContextCustomizer = builder.requestContextCustomizer;
        this.transparent = builder.transparent;
        this.pendingUrl = builder.url;

        if (GrapheneStartupPolicy.awaitsStartupOnCurrentThread()) {
            GrapheneCore.runtime();
        } else {
            GrapheneCore.startup();
        }

        GrapheneCefRuntime runtime = services.runtimeInternal();
        this.bridge = runtime.createBridge();
        this.nativeSlots = new GrapheneNativeSlotRegistry(this.bridge);
        this.loadListenerScope = new BrowserSurfaceLoadListenerScope(runtime.getLoadEventBus());
        this.loadListenerScope.add(new GrapheneLoadListener() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                nativeSlots.clearPageSlots();
            }

            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                if (!config.allowsTextSelection()) {
                    GraphenePageDefaults.disableTextSelection(browser, frame);
                }
                appliedCssViewport = null;
                applySurfaceCssPixels();
            }
        });
        if (builder.owner != null) {
            services.surfaceManager().register(builder.owner, this);
        }

        if (runtime.isInitialized()) {
            try {
                createBrowser();
            } catch (RuntimeException exception) {
                close();
                throw exception;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    GrapheneBrowser internalBrowser() {
        return browser;
    }

    public GrapheneBridge bridge() {
        return bridge;
    }

    public GrapheneNativeSlots nativeSlots() {
        return nativeSlots;
    }

    /**
     * Returns whether this surface is still waiting for Graphene to start before it can create its browser.
     * A starting surface renders nothing; {@link tytoo.grapheneui.api.widget.GrapheneWebViewWidget} draws a
     * native placeholder in its place.
     */
    public boolean isStarting() {
        return browser == null && !closed && !activationFailed;
    }

    /**
     * Returns whether Graphene started but this surface could not create its browser. Such a surface never renders.
     */
    public boolean hasFailed() {
        return activationFailed;
    }

    /**
     * Creates the browser now if Graphene has finished starting. Rendering does this automatically.
     *
     * @return {@code true} once the surface has a browser, {@code false} while Graphene is still starting
     */
    public boolean tryCreateBrowser() {
        return !closed && ensureBrowser();
    }

    public boolean canGoBack() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser != null && activeBrowser.canGoBack();
    }

    public boolean canGoForward() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser != null && activeBrowser.canGoForward();
    }

    public boolean isLoading() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser == null ? isStarting() : activeBrowser.isLoading();
    }

    public CursorType getRequestedCursor() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser == null ? CursorType.DEFAULT : activeBrowser.getRequestedCursor();
    }

    public String currentUrl() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser == null ? pendingUrl : activeBrowser.currentUrl();
    }

    public Optional<BrowserSurfacePerformanceSnapshot> performanceSnapshot() {
        GrapheneBrowser activeBrowser = browser;
        return activeBrowser == null ? Optional.empty() : activeBrowser.performanceSnapshot();
    }

    public BrowserSurfaceAccelerationStatus accelerationStatus() {
        return accelerationStatus;
    }

    public void loadUrl(String url) {
        String validatedUrl = Objects.requireNonNull(url, "url");
        nativeSlots.clearPageSlots();
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null) {
            pendingUrl = validatedUrl;
            return;
        }

        services.runtimeInternal().onNavigationRequested(activeBrowser);
        activeBrowser.loadURL(validatedUrl);
    }

    public void goBack() {
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null) {
            return;
        }

        nativeSlots.clearPageSlots();
        services.runtimeInternal().onNavigationRequested(activeBrowser);
        activeBrowser.goBack();
    }

    public void goForward() {
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null) {
            return;
        }

        nativeSlots.clearPageSlots();
        services.runtimeInternal().onNavigationRequested(activeBrowser);
        activeBrowser.goForward();
    }

    public void reload() {
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null) {
            return;
        }

        nativeSlots.clearPageSlots();
        services.runtimeInternal().onNavigationRequested(activeBrowser);
        activeBrowser.reload();
    }

    public int getSurfaceWidth() {
        return sizingState.surfaceWidth();
    }

    public int getSurfaceHeight() {
        return sizingState.surfaceHeight();
    }

    public int getResolutionWidth() {
        return sizingState.resolutionWidth();
    }

    public int getResolutionHeight() {
        return sizingState.resolutionHeight();
    }

    public Rectangle getViewBox() {
        return sizingState.viewBox();
    }

    public boolean isAutoResolution() {
        return sizingState.isAutoResolution();
    }

    public boolean allowsTextSelection() {
        return config.allowsTextSelection();
    }

    public boolean allowsZoom() {
        return config.allowsZoom();
    }

    public boolean allowsAltF4Close() {
        return config.allowsAltF4Close();
    }

    public void setOwner(Object owner) {
        ensureOpen();
        services.surfaceManager().register(Objects.requireNonNull(owner, OWNER_NAME), this);
    }

    public void clearOwner() {
        services.surfaceManager().unregister(this);
    }

    public Subscription subscribeLoadListener(GrapheneLoadListener loadListener) {
        return loadListenerScope.subscribe(loadListener);
    }

    public void addLoadListener(GrapheneLoadListener loadListener) {
        loadListenerScope.add(loadListener);
    }

    public void removeLoadListener(GrapheneLoadListener loadListener) {
        loadListenerScope.remove(loadListener);
    }

    public void setSurfaceSize(int width, int height) {
        BrowserSurfaceSizingState.ResizeInstruction resizeInstruction = sizingState.setSurfaceSize(
                width,
                height,
                McWindowScale.getScaleX(),
                McWindowScale.getScaleY()
        );
        applyResizeInstruction(resizeInstruction);
    }

    public void setResolution(int width, int height) {
        BrowserSurfaceSizingState.ResizeInstruction resizeInstruction = sizingState.setResolution(width, height);
        applyResizeInstruction(resizeInstruction);
    }

    public void useAutoResolution() {
        BrowserSurfaceSizingState.ResizeInstruction resizeInstruction = sizingState.useAutoResolution(
                McWindowScale.getScaleX(),
                McWindowScale.getScaleY()
        );
        applyResizeInstruction(resizeInstruction);
    }

    public void setViewBox(int x, int y, int width, int height) {
        sizingState.setViewBox(x, y, width, height);
    }

    public void resetViewBox() {
        sizingState.resetViewBox();
    }

    public Point toBrowserPoint(double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        return sizingState.toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight);
    }

    public int toBrowserX(double surfaceX, int renderedWidth) {
        return sizingState.toBrowserX(surfaceX, renderedWidth);
    }

    public int toBrowserY(double surfaceY, int renderedHeight) {
        return sizingState.toBrowserY(surfaceY, renderedHeight);
    }

    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        render(graphics, x, y, sizingState.surfaceWidth(), sizingState.surfaceHeight());
    }

    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (closed || !ensureBrowser()) {
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("graphene");
        try {
            pushBootstrap(profiler);
            pushRender(profiler, graphics, x, y, width, height);
        } finally {
            profiler.pop();
        }
    }

    public BrowserSurfaceTextureFrame prepareTextureFrame() {
        if (closed || !ensureBrowser()) {
            return null;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("graphene");
        try {
            pushBootstrap(profiler);
            return pushPrepareTextureFrame(profiler);
        } finally {
            profiler.pop();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        services.surfaceManager().unregister(this);
        nativeSlots.close();
        loadListenerScope.close();
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null) {
            bridge.close();
            return;
        }

        services.runtimeInternal().detachBridge(activeBrowser);
        activeBrowser.close();
    }

    private boolean ensureBrowser() {
        if (browser != null) {
            return true;
        }

        if (activationFailed) {
            return false;
        }

        GrapheneCefRuntime runtime = services.runtimeInternal();
        if (!runtime.isInitialized()) {
            if (runtime.isStartupRetryDue()) {
                GrapheneCore.startup();
            }

            return false;
        }

        try {
            createBrowser();
            return true;
        } catch (RuntimeException exception) {
            activationFailed = true;
            LOGGER.error("Failed to create the browser for a Graphene surface after startup", exception);
            return false;
        }
    }

    private void createBrowser() {
        GrapheneCefRuntime runtime = services.runtimeInternal();
        CefClient cefClient = explicitClient != null ? explicitClient : runtime.requireClient();
        CefRequestContext requestContext = explicitRequestContext != null
                ? explicitRequestContext
                : CefRequestContext.getGlobalContext();
        requestContextCustomizer.accept(requestContext);

        GrapheneBrowser createdBrowser = new GrapheneBrowser(
                cefClient,
                pendingUrl,
                transparent,
                requestContext,
                config.toCefBrowserSettings(),
                config.frameScheduling(),
                config.performanceMetricsEnabled()
        );
        try {
            loadListenerScope.bindBrowser(createdBrowser);
            runtime.attachBridge(createdBrowser, bridge);
        } catch (RuntimeException exception) {
            createdBrowser.close();
            throw exception;
        }

        browser = createdBrowser;
        createdBrowser.createImmediately();
        createdBrowser.wasResizedTo(sizingState.resolutionWidth(), sizingState.resolutionHeight());
    }

    private void applyResizeInstruction(BrowserSurfaceSizingState.ResizeInstruction resizeInstruction) {
        applySurfaceCssPixels();
        GrapheneBrowser activeBrowser = browser;
        if (activeBrowser == null || !resizeInstruction.shouldResizeBrowser()) {
            return;
        }

        activeBrowser.wasResizedTo(resizeInstruction.width(), resizeInstruction.height());
    }

    private void applySurfaceCssPixels() {
        GrapheneBrowser activeBrowser = browser;
        if (!config.usesSurfaceCssPixels() || closed || activeBrowser == null) {
            return;
        }

        BrowserSurfaceSizingState.CssViewport viewport = sizingState.cssViewport(config.cssPixelsPerSurfacePixel());
        if (viewport.equals(appliedCssViewport)) {
            return;
        }

        appliedCssViewport = viewport;
        GraphenePageDefaults.applySurfaceCssPixels(activeBrowser, viewport.width(), viewport.height(), viewport.zoom());
    }

    private void pushBootstrap(ProfilerFiller profiler) {
        profiler.push("bootstrap");
        try {
            services.runtimeInternal().ensureBootstrap(browser);
        } finally {
            profiler.pop();
        }
    }

    private void pushRender(ProfilerFiller profiler, GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        profiler.push("render");
        try {
            browser.render(
                    graphics,
                    x,
                    y,
                    width,
                    height,
                    sizingState.viewBoxX(),
                    sizingState.viewBoxY(),
                    sizingState.viewBoxWidth(),
                    sizingState.viewBoxHeight()
            );
            nativeSlots.render(
                    profiler,
                    graphics,
                    x,
                    y,
                    width,
                    height,
                    sizingState.resolutionWidth(),
                    sizingState.resolutionHeight(),
                    sizingState.viewBoxX(),
                    sizingState.viewBoxY(),
                    sizingState.viewBoxWidth(),
                    sizingState.viewBoxHeight()
            );
        } finally {
            profiler.pop();
        }
    }

    private BrowserSurfaceTextureFrame pushPrepareTextureFrame(ProfilerFiller profiler) {
        profiler.push("prepareTextureFrame");
        try {
            return browser.prepareMainFrameTexture(
                    sizingState.viewBoxX(),
                    sizingState.viewBoxY(),
                    sizingState.viewBoxWidth(),
                    sizingState.viewBoxHeight()
            );
        } finally {
            profiler.pop();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("BrowserSurface is closed");
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        void unsubscribe();

        @Override
        default void close() {
            unsubscribe();
        }
    }

    public static final class Builder {
        private String url = "about:blank";
        private boolean transparent = true;
        private int surfaceWidth = MIN_SIZE;
        private int surfaceHeight = MIN_SIZE;
        private boolean autoResolution = true;
        private int resolutionWidth = MIN_SIZE;
        private int resolutionHeight = MIN_SIZE;
        private Rectangle viewBox;
        private CefClient client;
        private CefRequestContext requestContext;
        private Consumer<CefRequestContext> requestContextCustomizer = NO_OP_REQUEST_CONTEXT_CUSTOMIZER;
        private BrowserSurfaceConfig config = BrowserSurfaceConfig.defaults();
        private Object owner;

        private Builder() {
        }

        private static int requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }

            return value;
        }

        public Builder url(String url) {
            this.url = Objects.requireNonNull(url, "url");
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        public Builder surfaceSize(int width, int height) {
            this.surfaceWidth = requirePositive(width, SURFACE_WIDTH_NAME);
            this.surfaceHeight = requirePositive(height, SURFACE_HEIGHT_NAME);
            return this;
        }

        public Builder resolution(int width, int height) {
            this.autoResolution = false;
            this.resolutionWidth = requirePositive(width, "resolutionWidth");
            this.resolutionHeight = requirePositive(height, "resolutionHeight");
            return this;
        }

        public Builder autoResolution() {
            this.autoResolution = true;
            return this;
        }

        public Builder viewBox(int x, int y, int width, int height) {
            this.viewBox = new Rectangle(x, y, width, height);
            return this;
        }

        public Builder client(CefClient client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        public Builder requestContext(CefRequestContext requestContext) {
            this.requestContext = Objects.requireNonNull(requestContext, "requestContext");
            return this;
        }

        public Builder requestContextCustomizer(Consumer<CefRequestContext> requestContextCustomizer) {
            this.requestContextCustomizer = this.requestContextCustomizer.andThen(
                    Objects.requireNonNull(requestContextCustomizer, "requestContextCustomizer")
            );
            return this;
        }

        public Builder owner(Object owner) {
            this.owner = Objects.requireNonNull(owner, OWNER_NAME);
            return this;
        }

        public Builder config(BrowserSurfaceConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public Builder maxFps(int maxFps) {
            this.config = this.config.withMaxFps(maxFps);
            return this;
        }

        public Builder allowTextSelection(boolean allowed) {
            this.config = this.config.withTextSelectionAllowed(allowed);
            return this;
        }

        public Builder allowZoom(boolean allowed) {
            this.config = this.config.withZoomAllowed(allowed);
            return this;
        }

        public Builder allowAltF4Close(boolean allowed) {
            this.config = this.config.withAltF4CloseAllowed(allowed);
            return this;
        }

        public Builder settingsCustomizer(Consumer<CefBrowserSettings> settingsCustomizer) {
            this.config = this.config.withSettingsCustomizer(settingsCustomizer);
            return this;
        }

        public Builder frameScheduling(BrowserSurfaceFrameScheduling frameScheduling) {
            this.config = this.config.withFrameScheduling(frameScheduling);
            return this;
        }

        public Builder performanceMetrics(boolean enabled) {
            this.config = this.config.withPerformanceMetrics(enabled);
            return this;
        }

        public Builder cssPixelsPerSurfacePixel(double ratio) {
            this.config = this.config.withCssPixelsPerSurfacePixel(ratio);
            return this;
        }

        public Builder preferAcceleratedPaint(boolean preferred) {
            this.config = this.config.withAcceleratedPaintPreference(preferred);
            return this;
        }

        public BrowserSurface build() {
            return new BrowserSurface(this);
        }
    }
}
