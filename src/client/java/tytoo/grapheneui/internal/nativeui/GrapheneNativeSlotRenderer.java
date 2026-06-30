package tytoo.grapheneui.internal.nativeui;

import java.util.Set;

interface GrapheneNativeSlotRenderer {
    Set<String> kinds();

    void render(GrapheneNativeSlotRenderContext context);
}
