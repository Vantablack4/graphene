package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;

final class GrapheneNativeSlot {
    private static final String DEFAULT_KIND = "default";

    private final String id;
    private final String kind;
    private final JsonObject payload;
    private final JsonObject renderOptions;
    private final JsonObject handoffOptions;
    private final GrapheneNativeSlotDomRect rect;
    private final boolean visible;

    private GrapheneNativeSlot(
            String id,
            String kind,
            JsonObject payload,
            JsonObject renderOptions,
            JsonObject handoffOptions,
            GrapheneNativeSlotDomRect rect,
            boolean visible
    ) {
        this.id = id;
        this.kind = kind;
        this.payload = payload;
        this.renderOptions = renderOptions;
        this.handoffOptions = handoffOptions;
        this.rect = rect;
        this.visible = visible;
    }

    static GrapheneNativeSlot fromPayload(GrapheneNativeSlotPayload payload) {
        if (payload == null || payload.id == null || payload.id.isBlank()) {
            return null;
        }

        String kind = payload.kind == null || payload.kind.isBlank() ? DEFAULT_KIND : payload.kind.trim();
        boolean attached = payload.attached == null || payload.attached;
        boolean connected = payload.connected == null || payload.connected;
        boolean visible = payload.visible == null || payload.visible;
        return new GrapheneNativeSlot(
                payload.id.trim(),
                kind,
                GrapheneNativeSlotJson.object(payload.payload),
                GrapheneNativeSlotJson.object(payload.render),
                GrapheneNativeSlotJson.object(payload.handoff),
                GrapheneNativeSlotDomRect.fromPayload(payload.rect),
                attached && connected && visible
        );
    }

    String id() {
        return id;
    }

    String kind() {
        return kind;
    }

    JsonObject payload() {
        return payload;
    }

    JsonObject renderOptions() {
        return renderOptions;
    }

    JsonObject handoffOptions() {
        return handoffOptions;
    }

    GrapheneNativeSlotDomRect rect() {
        return rect;
    }

    boolean shouldRender() {
        return visible
                && rect != null
                && GrapheneNativeSlotJson.booleanValue(renderOptions, true, "visible", "enabled");
    }

    int zIndex() {
        return GrapheneNativeSlotJson.intValue(renderOptions, 0, "zIndex", "z");
    }
}
