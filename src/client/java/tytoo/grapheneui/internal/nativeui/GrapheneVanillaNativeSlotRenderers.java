package tytoo.grapheneui.internal.nativeui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GrapheneVanillaNativeSlotRenderers {
    private final Map<String, GrapheneNativeSlotRenderer> renderersByKind = new HashMap<>();

    GrapheneVanillaNativeSlotRenderers() {
        List<GrapheneNativeSlotRenderer> renderers = List.of(
                new GrapheneVanillaItemSlotRenderer(),
                new GrapheneVanillaBlockSlotRenderer(),
                new GrapheneVanillaHeadSlotRenderer(),
                new GrapheneVanillaSkinSlotRenderer(),
                new GrapheneVanillaEntitySlotRenderer()
        );

        for (GrapheneNativeSlotRenderer renderer : renderers) {
            for (String kind : renderer.kinds()) {
                renderersByKind.put(normalize(kind), renderer);
            }
        }
    }

    GrapheneNativeSlotRenderer renderer(String kind) {
        return renderersByKind.get(normalize(kind));
    }

    private String normalize(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }
}
