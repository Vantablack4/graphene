package tytoo.grapheneui.api.world;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrapheneWorldAnchorTest {
    @Test
    void buildsAnchorWithPayloadAndOptions() {
        JsonObject payload = new JsonObject();
        payload.addProperty("label", "Harvest");

        GrapheneWorldAnchor anchor = GrapheneWorldAnchor.builder("crop:1", new Vec3(1.0D, 2.0D, 3.0D))
                .kind("crop")
                .maxDistance(12.0D)
                .priority(4)
                .screenOffset(3, -8)
                .interactive(true)
                .occlusion(GrapheneWorldAnchorOcclusion.THROTTLED_RAYCAST)
                .payload(payload)
                .build();

        assertEquals("crop:1", anchor.id());
        assertEquals("crop", anchor.kind());
        assertEquals(12.0D, anchor.maxDistance());
        assertEquals(4, anchor.priority());
        assertEquals(3, anchor.screenOffsetX());
        assertEquals(-8, anchor.screenOffsetY());
        assertEquals(GrapheneWorldAnchorOcclusion.THROTTLED_RAYCAST, anchor.occlusion());
        assertSame(payload, anchor.payload());
    }

    @Test
    void rejectsBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GrapheneWorldAnchor.builder(" ", Vec3.ZERO).build()
        );
    }

    @Test
    void rejectsNonPositiveExplicitMaxDistance() {
        GrapheneWorldAnchor.Builder builder = GrapheneWorldAnchor.builder("crop:1", Vec3.ZERO);

        assertThrows(IllegalArgumentException.class, () -> builder.maxDistance(0.0D));
        assertThrows(IllegalArgumentException.class, () -> builder.maxDistance(-0.5D));
        assertThrows(IllegalArgumentException.class, () -> builder.maxDistance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.maxDistance(Double.POSITIVE_INFINITY));
    }

    @Test
    void acceptsDefaultMaxDistanceSentinel() {
        GrapheneWorldAnchor anchor = GrapheneWorldAnchor.builder("crop:1", Vec3.ZERO)
                .maxDistance(GrapheneWorldAnchor.DEFAULT_MAX_DISTANCE)
                .build();

        assertEquals(GrapheneWorldAnchor.DEFAULT_MAX_DISTANCE, anchor.maxDistance());
    }
}
