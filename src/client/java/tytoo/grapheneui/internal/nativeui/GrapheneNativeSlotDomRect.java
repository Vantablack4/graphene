package tytoo.grapheneui.internal.nativeui;

record GrapheneNativeSlotDomRect(double left, double top, double right, double bottom, double width, double height) {
    static GrapheneNativeSlotDomRect fromPayload(GrapheneNativeSlotPayload.RectPayload payload) {
        if (payload == null) {
            return null;
        }

        double left = finite(payload.left, finite(payload.x, 0.0));
        double top = finite(payload.top, finite(payload.y, 0.0));
        double width = finite(payload.width, difference(payload.right, left));
        double height = finite(payload.height, difference(payload.bottom, top));
        double right = finite(payload.right, left + width);
        double bottom = finite(payload.bottom, top + height);

        return new GrapheneNativeSlotDomRect(left, top, right, bottom, right - left, bottom - top);
    }

    private static double finite(Double value, double fallback) {
        if (value != null && Double.isFinite(value)) {
            return value;
        }

        return Double.isFinite(fallback) ? fallback : 0.0;
    }

    private static double difference(Double value, double baseline) {
        if (value != null && Double.isFinite(value) && Double.isFinite(baseline)) {
            return value - baseline;
        }

        return 0.0;
    }
}
