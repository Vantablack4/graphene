package tytoo.grapheneui.internal.nativeui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GrapheneNativeSlotBoundsMapperTest {
    @Test
    void domRectPayloadCanUsePositionAndSizeWithoutEdges() {
        GrapheneNativeSlotPayload.RectPayload payload = new GrapheneNativeSlotPayload.RectPayload();
        payload.x = 10.0;
        payload.y = 20.0;
        payload.width = 30.0;
        payload.height = 40.0;

        GrapheneNativeSlotDomRect rect = GrapheneNativeSlotDomRect.fromPayload(payload);

        assertEquals(new GrapheneNativeSlotDomRect(10.0, 20.0, 40.0, 60.0, 30.0, 40.0), rect);
    }

    @Test
    void mapDomRectToScreenRectUsesSurfaceOrigin() {
        GrapheneNativeSlotScreenRect mapped = GrapheneNativeSlotBoundsMapper.map(
                new GrapheneNativeSlotDomRect(10.0, 20.0, 30.0, 40.0, 20.0, 20.0),
                new GrapheneNativeSlotViewport(100.0, 100.0, 0.0, 0.0),
                new GrapheneNativeSlotRenderTarget(50, 60, 200, 100, 100, 100, 0, 0, 100, 100)
        );

        assertEquals(new GrapheneNativeSlotScreenRect(70, 80, 40, 20), mapped);
    }

    @Test
    void mapDomRectToScreenRectAppliesViewBox() {
        GrapheneNativeSlotScreenRect mapped = GrapheneNativeSlotBoundsMapper.map(
                new GrapheneNativeSlotDomRect(25.0, 25.0, 75.0, 75.0, 50.0, 50.0),
                new GrapheneNativeSlotViewport(100.0, 100.0, 0.0, 0.0),
                new GrapheneNativeSlotRenderTarget(0, 0, 100, 100, 100, 100, 25, 25, 50, 50)
        );

        assertEquals(new GrapheneNativeSlotScreenRect(0, 0, 100, 100), mapped);
    }

    @Test
    void mapDomRectToScreenRectClampsOutsideSurface() {
        GrapheneNativeSlotScreenRect mapped = GrapheneNativeSlotBoundsMapper.map(
                new GrapheneNativeSlotDomRect(-10.0, -10.0, 20.0, 20.0, 30.0, 30.0),
                new GrapheneNativeSlotViewport(100.0, 100.0, 0.0, 0.0),
                new GrapheneNativeSlotRenderTarget(0, 0, 100, 100, 100, 100, 0, 0, 100, 100)
        );

        assertEquals(new GrapheneNativeSlotScreenRect(0, 0, 20, 20), mapped);
    }

    @Test
    void mapClipRoundsInwardWhereMapRoundsOutward() {
        GrapheneNativeSlotDomRect rect = new GrapheneNativeSlotDomRect(10.5, 20.5, 30.5, 40.5, 20.0, 20.0);
        GrapheneNativeSlotViewport viewport = new GrapheneNativeSlotViewport(100.0, 100.0, 0.0, 0.0);
        GrapheneNativeSlotRenderTarget target = new GrapheneNativeSlotRenderTarget(0, 0, 100, 100, 100, 100, 0, 0, 100, 100);

        assertEquals(new GrapheneNativeSlotScreenRect(10, 20, 21, 21), GrapheneNativeSlotBoundsMapper.map(rect, viewport, target));
        assertEquals(new GrapheneNativeSlotScreenRect(11, 21, 19, 19), GrapheneNativeSlotBoundsMapper.mapClip(rect, viewport, target));
    }

    @Test
    void slotKeepsItsClipFromThePayload() {
        GrapheneNativeSlotPayload payload = new GrapheneNativeSlotPayload();
        payload.id = "head";
        payload.rect = new GrapheneNativeSlotPayload.RectPayload();
        payload.rect.left = 0.0;
        payload.rect.top = 0.0;
        payload.rect.width = 10.0;
        payload.rect.height = 10.0;
        payload.clip = new GrapheneNativeSlotPayload.RectPayload();
        payload.clip.left = 0.0;
        payload.clip.top = 4.0;
        payload.clip.width = 10.0;
        payload.clip.height = 20.0;

        GrapheneNativeSlot slot = GrapheneNativeSlot.fromPayload(payload);

        assertEquals(new GrapheneNativeSlotDomRect(0.0, 4.0, 10.0, 24.0, 10.0, 20.0), slot.clip());
        assertNull(GrapheneNativeSlot.fromPayload(withoutClip()).clip());
    }

    private static GrapheneNativeSlotPayload withoutClip() {
        GrapheneNativeSlotPayload payload = new GrapheneNativeSlotPayload();
        payload.id = "head";
        return payload;
    }

    @Test
    void mapDomRectToScreenRectHidesEmptyRects() {
        GrapheneNativeSlotScreenRect mapped = GrapheneNativeSlotBoundsMapper.map(
                new GrapheneNativeSlotDomRect(10.0, 10.0, 10.0, 20.0, 0.0, 10.0),
                new GrapheneNativeSlotViewport(100.0, 100.0, 0.0, 0.0),
                new GrapheneNativeSlotRenderTarget(0, 0, 100, 100, 100, 100, 0, 0, 100, 100)
        );

        assertNull(mapped);
    }
}
