package tytoo.grapheneui.api.surface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserSurfaceConfigTest {
    @Test
    void defaultsUseLibraryDefaultMaxFps() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.defaults();

        assertEquals(60, config.toCefBrowserSettings().windowless_frame_rate);
        assertFalse(config.allowsTextSelection());
        assertFalse(config.allowsZoom());
        assertFalse(config.allowsAltF4Close());
        assertFalse(config.toCefBrowserSettings().external_begin_frame_enabled);
        assertFalse(config.performanceMetricsEnabled());
    }

    @Test
    void builderCanAllowBrowserInteractions() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .allowTextSelection(true)
                .allowZoom(true)
                .allowAltF4Close(true)
                .build();

        assertTrue(config.allowsTextSelection());
        assertTrue(config.allowsZoom());
        assertTrue(config.allowsAltF4Close());
    }

    @Test
    void derivedConfigsPreserveBrowserInteractionPolicy() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .allowTextSelection(true)
                .allowZoom(true)
                .allowAltF4Close(true)
                .build()
                .withMaxFps(144)
                .withFrameScheduling(BrowserSurfaceFrameScheduling.RENDER_DRIVEN)
                .withPerformanceMetrics(true)
                .withAcceleratedPaintPreference(true)
                .withSettingsCustomizer(settings -> settings.windowless_frame_rate = 165);

        assertTrue(config.allowsTextSelection());
        assertTrue(config.allowsZoom());
        assertTrue(config.allowsAltF4Close());
        assertEquals(165, config.toCefBrowserSettings().windowless_frame_rate);
        assertTrue(config.toCefBrowserSettings().external_begin_frame_enabled);
        assertTrue(config.performanceMetricsEnabled());
        assertTrue(config.acceleratedPaintPreferred());
    }

    @Test
    void withMaxFpsKeepsLargestExplicitValue() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.defaults()
                .withMaxFps(30)
                .withMaxFps(144)
                .withMaxFps(120);

        assertEquals(144, config.toCefBrowserSettings().windowless_frame_rate);
    }

    @Test
    void withMaxFpsOverrideReplacesExplicitValue() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.defaults()
                .withMaxFps(144)
                .withMaxFpsOverride(30);

        assertEquals(30, config.toCefBrowserSettings().windowless_frame_rate);
    }

    @Test
    void builderMaxFpsKeepsLargestExplicitValue() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .maxFps(72)
                .maxFps(165)
                .maxFps(144)
                .build();

        assertEquals(165, config.toCefBrowserSettings().windowless_frame_rate);
    }

    @Test
    void renderDrivenSchedulingEnablesExternalBeginFrames() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .frameScheduling(BrowserSurfaceFrameScheduling.RENDER_DRIVEN)
                .performanceMetrics(true)
                .build();

        assertTrue(config.toCefBrowserSettings().external_begin_frame_enabled);
        assertTrue(config.performanceMetricsEnabled());
    }

    @Test
    void typedFrameSchedulingOwnsTheLowLevelCefFlag() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .settingsCustomizer(settings -> settings.external_begin_frame_enabled = true)
                .build();

        assertFalse(config.toCefBrowserSettings().external_begin_frame_enabled);
    }

    @Test
    void unsafeSharedTextureCustomizerIsRejectedWithThePublicReason() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .settingsCustomizer(settings -> settings.shared_texture_enabled = true)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::toCefBrowserSettings
        );
        assertEquals(BrowserSurfaceAccelerationStatus.SHARED_TEXTURE_UNAVAILABLE_REASON, exception.getMessage());
    }

    @Test
    void acceleratedPaintPreferenceRemainsAnObservableSoftwareFallback() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
                .preferAcceleratedPaint(true)
                .build();
        BrowserSurfaceAccelerationStatus status = BrowserSurfaceAccelerationStatus.softwareFallback(
                config.acceleratedPaintPreferred()
        );

        assertTrue(status.sharedTextureRequested());
        assertFalse(status.sharedTextureActive());
        assertEquals(BrowserSurfaceAccelerationStatus.SOFTWARE_OSR_BUFFER_PATH, status.activePath());
        assertEquals(
                BrowserSurfaceAccelerationStatus.SHARED_TEXTURE_UNAVAILABLE_REASON,
                status.sharedTextureUnavailableReason()
        );
    }
}
