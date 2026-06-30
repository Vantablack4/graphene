package tytoo.grapheneui.internal.world;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.api.world.GrapheneWorldOverlayConfig;
import tytoo.grapheneui.api.world.GrapheneWorldOverlayLayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class GrapheneWorldOverlayManager {
    private static final AtomicInteger NEXT_LAYER_SEQUENCE = new AtomicInteger();
    private static final Object OWNER_LOCK = new Object();
    private static final Map<Object, List<GrapheneWorldOverlayLayerImpl>> LAYERS_BY_OWNER = new IdentityHashMap<>();

    private GrapheneWorldOverlayManager() {
    }

    public static GrapheneWorldOverlayLayer create(GrapheneHandle handle, GrapheneWorldOverlayConfig config) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(config, "config");

        Identifier elementId = config.elementId() != null
                ? config.elementId()
                : Identifier.fromNamespaceAndPath(handle.id(), "graphene_world_overlay_" + NEXT_LAYER_SEQUENCE.incrementAndGet());
        GrapheneWorldOverlayLayerImpl layer = new GrapheneWorldOverlayLayerImpl(elementId, config);
        HudElementRegistry.attachElementAfter(config.renderAfter(), elementId, layer);
        registerOwner(config.owner(), layer);
        return layer;
    }

    public static void closeOwned(Object owner) {
        Objects.requireNonNull(owner, "owner");
        List<GrapheneWorldOverlayLayerImpl> layers;
        synchronized (OWNER_LOCK) {
            layers = LAYERS_BY_OWNER.remove(owner);
        }

        if (layers == null) {
            return;
        }

        for (GrapheneWorldOverlayLayerImpl layer : layers) {
            layer.close();
        }
    }

    static void unregister(GrapheneWorldOverlayLayerImpl layer) {
        synchronized (OWNER_LOCK) {
            for (List<GrapheneWorldOverlayLayerImpl> layers : LAYERS_BY_OWNER.values()) {
                layers.remove(layer);
            }
            LAYERS_BY_OWNER.values().removeIf(List::isEmpty);
        }
    }

    private static void registerOwner(Object owner, GrapheneWorldOverlayLayerImpl layer) {
        if (owner == null) {
            return;
        }

        synchronized (OWNER_LOCK) {
            LAYERS_BY_OWNER.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(layer);
        }
    }
}
