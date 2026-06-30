package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonElement;

final class GrapheneNativeSlotPayload {
    String id;
    String kind;
    JsonElement payload;
    JsonElement render;
    JsonElement handoff;
    Boolean attached;
    Boolean connected;
    Boolean visible;
    RectPayload rect;

    static final class RectPayload {
        Double x;
        Double y;
        Double left;
        Double top;
        Double right;
        Double bottom;
        Double width;
        Double height;
    }
}
