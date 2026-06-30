package tytoo.grapheneui.internal.nativeui;

record GrapheneNativeSlotPointer(boolean available, int x, int y) {
    static GrapheneNativeSlotPointer unavailable() {
        return new GrapheneNativeSlotPointer(false, 0, 0);
    }

    static GrapheneNativeSlotPointer at(int x, int y) {
        return new GrapheneNativeSlotPointer(true, x, y);
    }
}
