package tytoo.grapheneui.api.surface;

import org.cef.CefBrowserSettings;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration class for browser surface settings, including frame rate and custom browser settings.
 * Provides a builder for easy configuration and immutability.
 */
public final class BrowserSurfaceConfig {
    private static final int DEFAULT_MAX_FPS = 60;
    private static final boolean DEFAULT_TEXT_SELECTION_ALLOWED = false;
    private static final boolean DEFAULT_ZOOM_ALLOWED = false;
    private static final boolean DEFAULT_ALT_F4_CLOSE_ALLOWED = false;
    private static final Consumer<CefBrowserSettings> NO_OP_SETTINGS_CUSTOMIZER = ignoredSettings -> {
    };
    private static final BrowserSurfaceConfig DEFAULT = new Builder().build();
    private static final String SETTINGS_CUSTOMIZER = "settingsCustomizer";

    private final Integer windowlessFrameRate;
    private final boolean windowlessFrameRateExplicit;
    private final boolean textSelectionAllowed;
    private final boolean zoomAllowed;
    private final boolean altF4CloseAllowed;
    private final Consumer<CefBrowserSettings> settingsCustomizer;
    private final BrowserSurfaceFrameScheduling frameScheduling;
    private final boolean performanceMetricsEnabled;
    private final boolean acceleratedPaintPreferred;
    private final double cssPixelsPerSurfacePixel;

    private BrowserSurfaceConfig(Builder builder) {
        this(
                builder.windowlessFrameRate,
                builder.windowlessFrameRateExplicit,
                builder.textSelectionAllowed,
                builder.zoomAllowed,
                builder.altF4CloseAllowed,
                builder.settingsCustomizer,
                builder.frameScheduling,
                builder.performanceMetricsEnabled,
                builder.acceleratedPaintPreferred,
                builder.cssPixelsPerSurfacePixel
        );
    }

    private BrowserSurfaceConfig(
            Integer windowlessFrameRate,
            boolean windowlessFrameRateExplicit,
            boolean textSelectionAllowed,
            boolean zoomAllowed,
            boolean altF4CloseAllowed,
            Consumer<CefBrowserSettings> settingsCustomizer,
            BrowserSurfaceFrameScheduling frameScheduling,
            boolean performanceMetricsEnabled,
            boolean acceleratedPaintPreferred,
            double cssPixelsPerSurfacePixel
    ) {
        this.windowlessFrameRate = windowlessFrameRate;
        this.windowlessFrameRateExplicit = windowlessFrameRateExplicit;
        this.textSelectionAllowed = textSelectionAllowed;
        this.zoomAllowed = zoomAllowed;
        this.altF4CloseAllowed = altF4CloseAllowed;
        this.settingsCustomizer = Objects.requireNonNullElse(settingsCustomizer, NO_OP_SETTINGS_CUSTOMIZER);
        this.frameScheduling = Objects.requireNonNull(frameScheduling, "frameScheduling");
        this.performanceMetricsEnabled = performanceMetricsEnabled;
        this.acceleratedPaintPreferred = acceleratedPaintPreferred;
        this.cssPixelsPerSurfacePixel = cssPixelsPerSurfacePixel;
    }

    public static BrowserSurfaceConfig defaults() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void validateFrameRate(int maxFps) {
        if (maxFps <= 0) {
            throw new IllegalArgumentException("maxFps must be > 0");
        }
    }

    private static double validateCssPixelRatio(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0.0) {
            throw new IllegalArgumentException("cssPixelsPerSurfacePixel must be finite and >= 0");
        }

        return ratio;
    }

    public BrowserSurfaceConfig withMaxFps(int maxFps) {
        validateFrameRate(maxFps);
        int mergedFrameRate = windowlessFrameRateExplicit
                ? Math.max(windowlessFrameRate, maxFps)
                : maxFps;
        return copy(
                mergedFrameRate,
                true,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withMaxFpsOverride(int maxFps) {
        validateFrameRate(maxFps);
        return copy(
                maxFps,
                true,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withTextSelectionAllowed(boolean allowed) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                allowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withZoomAllowed(boolean allowed) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                allowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withAltF4CloseAllowed(boolean allowed) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                allowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withSettingsCustomizer(Consumer<CefBrowserSettings> settingsCustomizer) {
        Consumer<CefBrowserSettings> nonNullCustomizer = Objects.requireNonNull(settingsCustomizer, SETTINGS_CUSTOMIZER);
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                this.settingsCustomizer.andThen(nonNullCustomizer),
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withFrameScheduling(BrowserSurfaceFrameScheduling frameScheduling) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                Objects.requireNonNull(frameScheduling, "frameScheduling"),
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withPerformanceMetrics(boolean enabled) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                enabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public BrowserSurfaceConfig withAcceleratedPaintPreference(boolean preferred) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                preferred,
                cssPixelsPerSurfacePixel
        );
    }

    /**
     * Lays the page out in surface pixels instead of browser pixels.
     * <p>
     * When positive, Graphene zooms the page root so one surface pixel spans {@code ratio} CSS pixels while the
     * browser keeps rendering at full window resolution. For a widget sized in Minecraft GUI pixels, {@code 1.0}
     * makes one CSS pixel one GUI pixel and {@code 2.0} matches a 2x pixel-art design grid, so the page follows the
     * player's GUI Scale. Root zoom does not affect media queries or viewport units, so responsive pages should
     * query a full-size container ({@code container-type: size}) and use container units instead. Graphene marks
     * the root with {@code data-graphene-surface}, sets {@code --graphene-surface-width} and
     * {@code --graphene-surface-height}, exposes {@code globalThis.grapheneSurface} and dispatches a
     * {@code graphene:surface} event whenever the surface changes. Zero keeps one CSS pixel per browser pixel.
     */
    public BrowserSurfaceConfig withCssPixelsPerSurfacePixel(double ratio) {
        return copy(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                validateCssPixelRatio(ratio)
        );
    }

    public boolean allowsTextSelection() {
        return textSelectionAllowed;
    }

    public boolean allowsZoom() {
        return zoomAllowed;
    }

    public boolean allowsAltF4Close() {
        return altF4CloseAllowed;
    }

    public BrowserSurfaceFrameScheduling frameScheduling() {
        return frameScheduling;
    }

    public boolean performanceMetricsEnabled() {
        return performanceMetricsEnabled;
    }

    public boolean acceleratedPaintPreferred() {
        return acceleratedPaintPreferred;
    }

    public double cssPixelsPerSurfacePixel() {
        return cssPixelsPerSurfacePixel;
    }

    public boolean usesSurfaceCssPixels() {
        return cssPixelsPerSurfacePixel > 0.0;
    }

    public CefBrowserSettings toCefBrowserSettings() {
        CefBrowserSettings cefBrowserSettings = new CefBrowserSettings();
        if (windowlessFrameRate != null) {
            cefBrowserSettings.windowless_frame_rate = windowlessFrameRate;
        }

        settingsCustomizer.accept(cefBrowserSettings);
        if (cefBrowserSettings.shared_texture_enabled) {
            throw new IllegalArgumentException(BrowserSurfaceAccelerationStatus.SHARED_TEXTURE_UNAVAILABLE_REASON);
        }
        cefBrowserSettings.external_begin_frame_enabled = frameScheduling == BrowserSurfaceFrameScheduling.RENDER_DRIVEN;
        return cefBrowserSettings;
    }

    private static BrowserSurfaceConfig copy(
            Integer windowlessFrameRate,
            boolean windowlessFrameRateExplicit,
            boolean textSelectionAllowed,
            boolean zoomAllowed,
            boolean altF4CloseAllowed,
            Consumer<CefBrowserSettings> settingsCustomizer,
            BrowserSurfaceFrameScheduling frameScheduling,
            boolean performanceMetricsEnabled,
            boolean acceleratedPaintPreferred,
            double cssPixelsPerSurfacePixel
    ) {
        return new BrowserSurfaceConfig(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer,
                frameScheduling,
                performanceMetricsEnabled,
                acceleratedPaintPreferred,
                cssPixelsPerSurfacePixel
        );
    }

    public static final class Builder {
        private int windowlessFrameRate = DEFAULT_MAX_FPS;
        private boolean windowlessFrameRateExplicit;
        private boolean textSelectionAllowed = DEFAULT_TEXT_SELECTION_ALLOWED;
        private boolean zoomAllowed = DEFAULT_ZOOM_ALLOWED;
        private boolean altF4CloseAllowed = DEFAULT_ALT_F4_CLOSE_ALLOWED;
        private Consumer<CefBrowserSettings> settingsCustomizer = NO_OP_SETTINGS_CUSTOMIZER;
        private BrowserSurfaceFrameScheduling frameScheduling = BrowserSurfaceFrameScheduling.AUTOMATIC;
        private boolean performanceMetricsEnabled;
        private boolean acceleratedPaintPreferred;
        private double cssPixelsPerSurfacePixel;

        private Builder() {
        }

        public Builder maxFps(int maxFps) {
            validateFrameRate(maxFps);
            this.windowlessFrameRate = windowlessFrameRateExplicit
                    ? Math.max(this.windowlessFrameRate, maxFps)
                    : maxFps;
            this.windowlessFrameRateExplicit = true;
            return this;
        }

        public Builder allowTextSelection(boolean allowed) {
            this.textSelectionAllowed = allowed;
            return this;
        }

        public Builder allowZoom(boolean allowed) {
            this.zoomAllowed = allowed;
            return this;
        }

        public Builder allowAltF4Close(boolean allowed) {
            this.altF4CloseAllowed = allowed;
            return this;
        }

        public Builder settingsCustomizer(Consumer<CefBrowserSettings> settingsCustomizer) {
            this.settingsCustomizer = this.settingsCustomizer.andThen(
                    Objects.requireNonNull(settingsCustomizer, SETTINGS_CUSTOMIZER)
            );
            return this;
        }

        public Builder frameScheduling(BrowserSurfaceFrameScheduling frameScheduling) {
            this.frameScheduling = Objects.requireNonNull(frameScheduling, "frameScheduling");
            return this;
        }

        public Builder performanceMetrics(boolean enabled) {
            this.performanceMetricsEnabled = enabled;
            return this;
        }

        public Builder preferAcceleratedPaint(boolean preferred) {
            this.acceleratedPaintPreferred = preferred;
            return this;
        }

        public Builder cssPixelsPerSurfacePixel(double ratio) {
            this.cssPixelsPerSurfacePixel = validateCssPixelRatio(ratio);
            return this;
        }

        public BrowserSurfaceConfig build() {
            return new BrowserSurfaceConfig(this);
        }
    }
}
