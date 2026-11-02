package tytoo.grapheneui.internal.nativeui;

final class GrapheneNativeSlotBoundsMapper {
    private GrapheneNativeSlotBoundsMapper() {
    }

    /** Maps a slot's DOM rect to screen pixels, rounding outward so the slot covers its whole box. */
    static GrapheneNativeSlotScreenRect map(
            GrapheneNativeSlotDomRect rect,
            GrapheneNativeSlotViewport viewport,
            GrapheneNativeSlotRenderTarget target
    ) {
        double[] edges = screenEdges(rect, viewport, target);
        if (edges == null) {
            return null;
        }

        return clampToTarget(GrapheneNativeSlotScreenRect.fromEdges(
                (int) Math.floor(edges[0]),
                (int) Math.floor(edges[1]),
                (int) Math.ceil(edges[2]),
                (int) Math.ceil(edges[3])
        ), target);
    }

    /**
     * Maps a clip rect like {@link #map}, but rounds inward, so a clip edge that falls inside a
     * screen pixel never lets a slot draw past the edge the page shows.
     */
    static GrapheneNativeSlotScreenRect mapClip(
            GrapheneNativeSlotDomRect rect,
            GrapheneNativeSlotViewport viewport,
            GrapheneNativeSlotRenderTarget target
    ) {
        double[] edges = screenEdges(rect, viewport, target);
        if (edges == null) {
            return null;
        }

        return clampToTarget(GrapheneNativeSlotScreenRect.fromEdges(
                (int) Math.ceil(edges[0]),
                (int) Math.ceil(edges[1]),
                (int) Math.floor(edges[2]),
                (int) Math.floor(edges[3])
        ), target);
    }

    /** Left, top, right and bottom in screen pixels, or null when nothing can be drawn. */
    private static double[] screenEdges(
            GrapheneNativeSlotDomRect rect,
            GrapheneNativeSlotViewport viewport,
            GrapheneNativeSlotRenderTarget target
    ) {
        if (rect == null || viewport == null || target == null) {
            return null;
        }

        if (rect.width() <= 0.0 || rect.height() <= 0.0 || viewport.width() <= 0.0 || viewport.height() <= 0.0) {
            return null;
        }

        if (target.width() <= 0 || target.height() <= 0 || target.viewBoxWidth() <= 0 || target.viewBoxHeight() <= 0) {
            return null;
        }

        double browserLeft = (rect.left() + viewport.offsetLeft()) * target.resolutionWidth() / viewport.width();
        double browserTop = (rect.top() + viewport.offsetTop()) * target.resolutionHeight() / viewport.height();
        double browserRight = (rect.right() + viewport.offsetLeft()) * target.resolutionWidth() / viewport.width();
        double browserBottom = (rect.bottom() + viewport.offsetTop()) * target.resolutionHeight() / viewport.height();

        return new double[] {
                target.x() + (browserLeft - target.viewBoxX()) * target.width() / target.viewBoxWidth(),
                target.y() + (browserTop - target.viewBoxY()) * target.height() / target.viewBoxHeight(),
                target.x() + (browserRight - target.viewBoxX()) * target.width() / target.viewBoxWidth(),
                target.y() + (browserBottom - target.viewBoxY()) * target.height() / target.viewBoxHeight()
        };
    }

    private static GrapheneNativeSlotScreenRect clampToTarget(
            GrapheneNativeSlotScreenRect unclamped,
            GrapheneNativeSlotRenderTarget target
    ) {
        if (unclamped == null) {
            return null;
        }

        return unclamped.intersect(GrapheneNativeSlotScreenRect.fromSize(target.x(), target.y(), target.width(), target.height()));
    }
}
