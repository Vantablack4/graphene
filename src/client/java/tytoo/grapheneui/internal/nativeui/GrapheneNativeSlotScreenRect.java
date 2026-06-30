package tytoo.grapheneui.internal.nativeui;

record GrapheneNativeSlotScreenRect(int x, int y, int width, int height) {
    static GrapheneNativeSlotScreenRect fromSize(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        return new GrapheneNativeSlotScreenRect(x, y, width, height);
    }

    static GrapheneNativeSlotScreenRect fromEdges(int left, int top, int right, int bottom) {
        return fromSize(left, top, right - left, bottom - top);
    }

    int right() {
        return x + width;
    }

    int bottom() {
        return y + height;
    }

    boolean contains(int px, int py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    GrapheneNativeSlotScreenRect intersect(GrapheneNativeSlotScreenRect other) {
        if (other == null) {
            return null;
        }

        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(right(), other.right());
        int bottom = Math.min(bottom(), other.bottom());
        return fromEdges(left, top, right, bottom);
    }
}
