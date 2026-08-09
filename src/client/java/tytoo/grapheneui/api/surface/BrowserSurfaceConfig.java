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
                builder.acceleratedPaintPreferred
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
            boolean acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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
                preferred
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
            boolean acceleratedPaintPreferred
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
                acceleratedPaintPreferred
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

        public BrowserSurfaceConfig build() {
            return new BrowserSurfaceConfig(this);
        }
    }
}
