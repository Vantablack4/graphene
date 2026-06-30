package tytoo.grapheneui.internal.world;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;
import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.api.world.GrapheneWorldSurface;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceConfig;
import tytoo.grapheneui.api.world.GrapheneWorldSurfacePick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GrapheneWorldSurfaceManager {
    private static final double DEFAULT_INTERACTION_RAY_LENGTH = 64.0D;
    private static final AtomicInteger NEXT_SURFACE_SEQUENCE = new AtomicInteger();
    private static final AtomicBoolean RENDER_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_TICK_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final Object SURFACE_LOCK = new Object();
    private static final Set<GrapheneWorldSurfaceImpl> SURFACES = new LinkedHashSet<>();
    private static final Map<Object, List<GrapheneWorldSurfaceImpl>> SURFACES_BY_OWNER = new IdentityHashMap<>();
    private static boolean primaryUseConsumedWhileDown;

    private GrapheneWorldSurfaceManager() {
    }

    public static GrapheneWorldSurface create(GrapheneHandle handle, GrapheneWorldSurfaceConfig config) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(config, "config");
        ensureRenderEventRegistered();
        ensureInputTickEventRegistered();

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

    public static Optional<GrapheneWorldSurfacePick> pickNearestFromRay(
            ResourceKey<Level> dimension,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double rayLength
    ) {
        Objects.requireNonNull(rayOrigin, "rayOrigin");
        Objects.requireNonNull(rayDirection, "rayDirection");
        List<GrapheneWorldSurfaceImpl> surfaces;
        synchronized (SURFACE_LOCK) {
            surfaces = List.copyOf(SURFACES);
        }

        return surfaces.stream()
                .map(surface -> surface.pick(dimension, rayOrigin, rayDirection, rayLength))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(GrapheneWorldSurfacePick::distance));
    }

    public static boolean handlePrimaryClickFromCameraRay(Minecraft client) {
        ensureInputTickEventRegistered();
        if (client == null || client.level == null || client.player == null || client.screen != null) {
            return false;
        }
        if (client.options == null || !client.options.keyUse.isDown()) {
            return false;
        }
        if (client.gameMode == null || client.gameMode.isDestroying() || client.player.isHandsBusy()) {
            return false;
        }

        Camera camera = client.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return false;
        }

        Vector3fc forward = camera.forwardVector();
        Optional<GrapheneWorldSurfacePick> pick = pickNearestFromRay(
                client.level.dimension(),
                camera.position(),
                new Vec3(forward.x(), forward.y(), forward.z()),
                DEFAULT_INTERACTION_RAY_LENGTH
        );
        if (pick.isEmpty()) {
            return primaryUseConsumedWhileDown;
        }

        if (!primaryUseConsumedWhileDown) {
            dispatchPrimaryClick(pick.get());
            primaryUseConsumedWhileDown = true;
        }

        return true;
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

    private static void ensureInputTickEventRegistered() {
        if (!INPUT_TICK_EVENT_REGISTERED.compareAndSet(false, true)) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(GrapheneWorldSurfaceManager::tickInputState);
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

    private static void tickInputState(Minecraft client) {
        if (client == null || client.options == null || !client.options.keyUse.isDown()) {
            primaryUseConsumedWhileDown = false;
        }
    }

    private static void dispatchPrimaryClick(GrapheneWorldSurfacePick pick) {
        pick.surface().inputAdapter().mouseMoved(
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
        pick.surface().inputAdapter().setFocused(true);
        pick.surface().inputAdapter().mouseClicked(
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                false,
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
        pick.surface().inputAdapter().mouseReleased(
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
    }
}
