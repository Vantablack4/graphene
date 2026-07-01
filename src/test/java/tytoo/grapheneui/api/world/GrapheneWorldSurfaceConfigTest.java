package tytoo.grapheneui.api.world;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.surface.BrowserSurfaceConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneWorldSurfaceConfigTest {
    @Test
    void maxFpsOverridesCustomSurfaceConfigFrameRate() {
        BrowserSurfaceConfig highFpsSurfaceConfig = BrowserSurfaceConfig.defaults().withMaxFps(144);

        GrapheneWorldSurfaceConfig config = GrapheneWorldSurfaceConfig.builder("about:blank")
                .surfaceConfig(highFpsSurfaceConfig)
                .maxFps(20)
                .build();

        assertEquals(20, config.maxFps());
        assertEquals(20, config.surfaceConfig().toCefBrowserSettings().windowless_frame_rate);
    }
}
