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

    private BrowserSurfaceConfig(Builder builder) {
        this.windowlessFrameRate = builder.windowlessFrameRate;
        this.windowlessFrameRateExplicit = builder.windowlessFrameRateExplicit;
        this.textSelectionAllowed = builder.textSelectionAllowed;
        this.zoomAllowed = builder.zoomAllowed;
        this.altF4CloseAllowed = builder.altF4CloseAllowed;
        this.settingsCustomizer = Objects.requireNonNullElse(builder.settingsCustomizer, NO_OP_SETTINGS_CUSTOMIZER);
    }

    private BrowserSurfaceConfig(
            Integer windowlessFrameRate,
            boolean windowlessFrameRateExplicit,
            boolean textSelectionAllowed,
            boolean zoomAllowed,
            boolean altF4CloseAllowed,
            Consumer<CefBrowserSettings> settingsCustomizer
    ) {
        this.windowlessFrameRate = windowlessFrameRate;
        this.windowlessFrameRateExplicit = windowlessFrameRateExplicit;
        this.textSelectionAllowed = textSelectionAllowed;
        this.zoomAllowed = zoomAllowed;
        this.altF4CloseAllowed = altF4CloseAllowed;
        this.settingsCustomizer = Objects.requireNonNullElse(settingsCustomizer, NO_OP_SETTINGS_CUSTOMIZER);
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
        return new BrowserSurfaceConfig(
                mergedFrameRate,
                true,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer
        );
    }

    public BrowserSurfaceConfig withMaxFpsOverride(int maxFps) {
        validateFrameRate(maxFps);
        return new BrowserSurfaceConfig(
                maxFps,
                true,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer
        );
    }

    public BrowserSurfaceConfig withTextSelectionAllowed(boolean allowed) {
        return new BrowserSurfaceConfig(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                allowed,
                zoomAllowed,
                altF4CloseAllowed,
                settingsCustomizer
        );
    }

    public BrowserSurfaceConfig withZoomAllowed(boolean allowed) {
        return new BrowserSurfaceConfig(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                allowed,
                altF4CloseAllowed,
                settingsCustomizer
        );
    }

    public BrowserSurfaceConfig withAltF4CloseAllowed(boolean allowed) {
        return new BrowserSurfaceConfig(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                allowed,
                settingsCustomizer
        );
    }

    public BrowserSurfaceConfig withSettingsCustomizer(Consumer<CefBrowserSettings> settingsCustomizer) {
        Consumer<CefBrowserSettings> nonNullCustomizer = Objects.requireNonNull(settingsCustomizer, SETTINGS_CUSTOMIZER);
        return new BrowserSurfaceConfig(
                windowlessFrameRate,
                windowlessFrameRateExplicit,
                textSelectionAllowed,
                zoomAllowed,
                altF4CloseAllowed,
                this.settingsCustomizer.andThen(nonNullCustomizer)
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

    public CefBrowserSettings toCefBrowserSettings() {
        CefBrowserSettings cefBrowserSettings = new CefBrowserSettings();
        if (windowlessFrameRate != null) {
            cefBrowserSettings.windowless_frame_rate = windowlessFrameRate;
        }

        settingsCustomizer.accept(cefBrowserSettings);
        return cefBrowserSettings;
    }

    public static final class Builder {
        private int windowlessFrameRate = DEFAULT_MAX_FPS;
        private boolean windowlessFrameRateExplicit;
        private boolean textSelectionAllowed = DEFAULT_TEXT_SELECTION_ALLOWED;
        private boolean zoomAllowed = DEFAULT_ZOOM_ALLOWED;
        private boolean altF4CloseAllowed = DEFAULT_ALT_F4_CLOSE_ALLOWED;
        private Consumer<CefBrowserSettings> settingsCustomizer = NO_OP_SETTINGS_CUSTOMIZER;

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
            this.settingsCustomizer = this.settingsCustomizer.andThen(Objects.requireNonNull(settingsCustomizer, SETTINGS_CUSTOMIZER));
            return this;
        }

        public BrowserSurfaceConfig build() {
            return new BrowserSurfaceConfig(this);
        }
    }
}
