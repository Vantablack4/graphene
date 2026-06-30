package tytoo.grapheneui.internal.nativeui;

import java.util.List;

final class GrapheneNativeSlotFramePayload {
    int version;
    String pageId;
    long seq;
    GrapheneNativeSlotViewportPayload viewport;
    List<GrapheneNativeSlotPayload> slots;
    List<String> removed;
}
