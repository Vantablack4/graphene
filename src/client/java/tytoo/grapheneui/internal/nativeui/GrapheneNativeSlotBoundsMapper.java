package tytoo.grapheneui.internal.nativeui;

final class GrapheneNativeSlotBoundsMapper {
    private GrapheneNativeSlotBoundsMapper() {
    }

    static GrapheneNativeSlotScreenRect map(
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

        double screenLeft = target.x() + (browserLeft - target.viewBoxX()) * target.width() / target.viewBoxWidth();
        double screenTop = target.y() + (browserTop - target.viewBoxY()) * target.height() / target.viewBoxHeight();
        double screenRight = target.x() + (browserRight - target.viewBoxX()) * target.width() / target.viewBoxWidth();
        double screenBottom = target.y() + (browserBottom - target.viewBoxY()) * target.height() / target.viewBoxHeight();

        GrapheneNativeSlotScreenRect unclamped = GrapheneNativeSlotScreenRect.fromEdges(
                (int) Math.floor(screenLeft),
                (int) Math.floor(screenTop),
                (int) Math.ceil(screenRight),
                (int) Math.ceil(screenBottom)
        );
        if (unclamped == null) {
            return null;
        }

        return unclamped.intersect(GrapheneNativeSlotScreenRect.fromSize(target.x(), target.y(), target.width(), target.height()));
    }
}
