package tytoo.grapheneui.api.surface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserSurfaceConfigTest {
    @Test
    void defaultsUseLibraryDefaultMaxFps() {
        BrowserSurfaceConfig config = BrowserSurfaceConfig.defaults();

        assertEquals(60, config.toCefBrowserSettings().windowless_frame_rate);
        assertFalse(config.allowsTextSelection());
        assertFalse(config.allowsZoom());
        assertFalse(config.allowsAltF4Close());
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
                .withSettingsCustomizer(settings -> settings.shared_texture_enabled = true);

        assertTrue(config.allowsTextSelection());
        assertTrue(config.allowsZoom());
        assertTrue(config.allowsAltF4Close());
        assertEquals(144, config.toCefBrowserSettings().windowless_frame_rate);
        assertTrue(config.toCefBrowserSettings().shared_texture_enabled);
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
}
