package tytoo.grapheneui.internal.nativeui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneSkinSlotLayoutTest {
    @Test
    void containKeepsWidePreviewScaleWhileCenteringThePlayer() {
        GrapheneNativeSlotScreenRect bounds = new GrapheneNativeSlotScreenRect(20, 40, 260, 500);

        GrapheneSkinSlotLayout layout = GrapheneSkinSlotLayout.contain(bounds, 0.5F);

        assertEquals(144.44444F, layout.scale(), 0.0001F);
        assertModelCenter(bounds, layout, 0.5F);
    }

    @Test
    void containShrinksThePlayerToFitACompactPreview() {
        GrapheneNativeSlotScreenRect bounds = new GrapheneNativeSlotScreenRect(20, 40, 260, 165);

        GrapheneSkinSlotLayout layout = GrapheneSkinSlotLayout.contain(bounds, 0.5F);

        assertEquals(68.75F, layout.scale(), 0.0001F);
        assertModelCenter(bounds, layout, 0.5F);
        assertTrue(layout.scale() * 2.0F < bounds.height());
    }

    @Test
    void containCentersAnExplicitScaleOverride() {
        GrapheneNativeSlotScreenRect bounds = new GrapheneNativeSlotScreenRect(20, 40, 260, 500);

        GrapheneSkinSlotLayout layout = GrapheneSkinSlotLayout.contain(bounds, 0.5F, 96.0F);

        assertEquals(96.0F, layout.scale(), 0.0001F);
        assertModelCenter(bounds, layout, 0.5F);
    }

    @Test
    void legacyLayoutPreservesTheExistingScaleAndAnchor() {
        GrapheneNativeSlotScreenRect bounds = new GrapheneNativeSlotScreenRect(20, 40, 260, 500);

        GrapheneSkinSlotLayout layout = GrapheneSkinSlotLayout.legacy(bounds);

        assertEquals(143.0F, layout.scale(), 0.0001F);
        assertEquals(bounds.y(), layout.top());
        assertEquals(bounds.bottom(), layout.bottom());
    }

    private static void assertModelCenter(
            GrapheneNativeSlotScreenRect bounds,
            GrapheneSkinSlotLayout layout,
            float alignY
    ) {
        float expectedCenter = bounds.y() + bounds.height() * alignY;
        float actualCenter = layout.bottom() - layout.scale() * 1.101F;
        assertEquals(expectedCenter, actualCenter, 0.5F);
    }
}
