package tytoo.grapheneui.api.world;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrapheneWorldOverlayConfigTest {
    @Test
    void buildsConfigWithDefaults() {
        GrapheneWorldOverlayConfig config = GrapheneWorldOverlayConfig.builder("app://assets/test/world.html").build();

        assertEquals("app://assets/test/world.html", config.url());
        assertEquals(GrapheneWorldOverlayConfig.DEFAULT_MAX_ANCHORS, config.maxAnchors());
        assertEquals(GrapheneWorldOverlayConfig.DEFAULT_MAX_FPS, config.maxFps());
        assertEquals(GrapheneWorldOverlayConfig.DEFAULT_MAX_DISTANCE, config.defaultMaxDistance());
        assertEquals(GrapheneWorldOverlayConfig.DEFAULT_SCREEN_MARGIN, config.screenMargin());
        assertEquals(GrapheneWorldOverlayConfig.DEFAULT_OCCLUSION_INTERVAL_TICKS, config.occlusionIntervalTicks());
        assertEquals(VanillaHudElements.CROSSHAIR, config.renderAfter());
        assertFalse(config.renderWhenScreenOpen());
    }

    @Test
    void buildsConfigWithExplicitValues() {
        Object owner = new Object();
        Identifier elementId = Identifier.fromNamespaceAndPath("test", "world_overlay");

        GrapheneWorldOverlayConfig config = GrapheneWorldOverlayConfig.builder("about:blank")
                .elementId(elementId)
                .maxAnchors(16)
                .maxFps(24)
                .defaultMaxDistance(48.0D)
                .screenMargin(12)
                .occlusionIntervalTicks(3)
                .renderWhenScreenOpen(true)
                .owner(owner)
                .build();

        assertEquals(elementId, config.elementId());
        assertEquals(16, config.maxAnchors());
        assertEquals(24, config.maxFps());
        assertEquals(48.0D, config.defaultMaxDistance());
        assertEquals(12, config.screenMargin());
        assertEquals(3, config.occlusionIntervalTicks());
        assertTrue(config.renderWhenScreenOpen());
        assertSame(owner, config.owner());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder(" ").build());
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder("about:blank").maxAnchors(0));
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder("about:blank").maxFps(0));
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder("about:blank").defaultMaxDistance(0.0D));
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder("about:blank").screenMargin(-1));
        assertThrows(IllegalArgumentException.class, () -> GrapheneWorldOverlayConfig.builder("about:blank").occlusionIntervalTicks(0));
    }
}
