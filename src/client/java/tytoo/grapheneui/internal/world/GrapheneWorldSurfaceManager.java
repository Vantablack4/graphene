package tytoo.grapheneui.internal.world;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;
import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.api.world.GrapheneWorldSurface;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceConfig;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GrapheneWorldSurfaceManager {
    private static final AtomicInteger NEXT_SURFACE_SEQUENCE = new AtomicInteger();
    private static final AtomicBoolean RENDER_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final Object SURFACE_LOCK = new Object();
    private static final Set<GrapheneWorldSurfaceImpl> SURFACES = new LinkedHashSet<>();
    private static final Map<Object, List<GrapheneWorldSurfaceImpl>> SURFACES_BY_OWNER = new IdentityHashMap<>();

    private GrapheneWorldSurfaceManager() {
    }

    public static GrapheneWorldSurface create(GrapheneHandle handle, GrapheneWorldSurfaceConfig config) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(config, "config");
        ensureRenderEventRegistered();

        Identifier surfaceId = config.surfaceId() != null
                ? config.surfaceId()
                : Identifier.fromNamespaceAndPath(handle.id(), "graphene_world_surface_" + NEXT_SURFACE_SEQUENCE.incrementAndGet());
        GrapheneWorldSurfaceImpl surface = new GrapheneWorldSurfaceImpl(surfaceId, config);
        register(surface, config.owner());
        return surface;
    }

    public static void closeOwned(Object owner) {
        Objects.requireNonNull(owner, "owner");
        List<GrapheneWorldSurfaceImpl> surfaces;
        synchronized (SURFACE_LOCK) {
            surfaces = SURFACES_BY_OWNER.remove(owner);
        }

        if (surfaces == null) {
            return;
        }

        for (GrapheneWorldSurfaceImpl surface : surfaces) {
            surface.close();
        }
    }

    static void unregister(GrapheneWorldSurfaceImpl surface) {
        synchronized (SURFACE_LOCK) {
            SURFACES.remove(surface);
            for (List<GrapheneWorldSurfaceImpl> surfaces : SURFACES_BY_OWNER.values()) {
                surfaces.remove(surface);
            }
            SURFACES_BY_OWNER.values().removeIf(List::isEmpty);
        }
    }

    private static void ensureRenderEventRegistered() {
        if (!RENDER_EVENT_REGISTERED.compareAndSet(false, true)) {
            return;
        }

        LevelRenderEvents.COLLECT_SUBMITS.register(GrapheneWorldSurfaceManager::collectSubmits);
    }

    private static void register(GrapheneWorldSurfaceImpl surface, Object owner) {
        synchronized (SURFACE_LOCK) {
            SURFACES.add(surface);
            if (owner != null) {
                SURFACES_BY_OWNER.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(surface);
            }
        }
    }

    private static void collectSubmits(LevelRenderContext context) {
        List<GrapheneWorldSurfaceImpl> surfaces;
        synchronized (SURFACE_LOCK) {
            surfaces = List.copyOf(SURFACES);
        }

        for (GrapheneWorldSurfaceImpl surface : surfaces) {
            surface.collectSubmit(context);
        }
    }
}
