package tytoo.grapheneui.internal.nativeui;

record GrapheneNativeSlotViewport(double width, double height, double offsetLeft, double offsetTop) {
    static GrapheneNativeSlotViewport fromPayload(GrapheneNativeSlotViewportPayload payload, int fallbackWidth, int fallbackHeight) {
        if (payload == null) {
            return fromResolution(fallbackWidth, fallbackHeight);
        }

        double width = positive(payload.width, fallbackWidth);
        double height = positive(payload.height, fallbackHeight);
        return new GrapheneNativeSlotViewport(width, height, finite(payload.offsetLeft), finite(payload.offsetTop));
    }

    static GrapheneNativeSlotViewport fromResolution(int width, int height) {
        return new GrapheneNativeSlotViewport(Math.max(1, width), Math.max(1, height), 0.0, 0.0);
    }

    private static double positive(Double value, int fallback) {
        if (value != null && Double.isFinite(value) && value > 0.0) {
            return value;
        }

        return Math.max(1, fallback);
    }

    private static double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : 0.0;
    }
}
