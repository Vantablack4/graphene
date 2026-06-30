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
    private static final int WORLD_SURFACE_BROWSER_BUTTON = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private static final AtomicInteger NEXT_SURFACE_SEQUENCE = new AtomicInteger();
    private static final AtomicBoolean RENDER_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_TICK_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final Object SURFACE_LOCK = new Object();
    private static final Set<GrapheneWorldSurfaceImpl> SURFACES = new LinkedHashSet<>();
    private static final Map<Object, List<GrapheneWorldSurfaceImpl>> SURFACES_BY_OWNER = new IdentityHashMap<>();
    private static GrapheneWorldSurfacePick hoveredPick;
    private static GrapheneWorldSurfacePick activePrimaryPick;
    private static boolean primaryUsePressed;

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

    public static boolean handlePrimaryUseFromCameraRay(Minecraft client) {
        ensureInputTickEventRegistered();
        if (primaryUsePressed) {
            return true;
        }
        if (client == null || client.level == null || client.player == null || client.screen != null) {
            return false;
        }
        if (client.options == null || !client.options.keyUse.isDown()) {
            return false;
        }
        if (client.gameMode == null || client.gameMode.isDestroying() || client.player.isHandsBusy()) {
            return false;
        }

        Optional<GrapheneWorldSurfacePick> pick = pickNearestForInteractionFromCamera(client);
        if (pick.isEmpty()) {
            return false;
        }

        beginPrimaryUse(pick.get());
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
        clearSurfaceInputState(surface);
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
        if (!isWorldPointerAvailable(client)) {
            releasePrimaryUse();
            clearHoveredPick();
            return;
        }

        Optional<GrapheneWorldSurfacePick> pick = pickNearestForInteractionFromCamera(client);
        if (primaryUsePressed) {
            tickPrimaryUse(client, pick);
            return;
        }

        updateHoveredPick(pick);
    }

    private static boolean isWorldPointerAvailable(Minecraft client) {
        return client != null && client.options != null && client.level != null && client.player != null && client.screen == null;
    }

    private static Optional<GrapheneWorldSurfacePick> pickNearestForInteractionFromCamera(Minecraft client) {
        Camera camera = client.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return Optional.empty();
        }

        Vector3fc forward = camera.forwardVector();
        Vec3 rayDirection = new Vec3(forward.x(), forward.y(), forward.z());
        List<GrapheneWorldSurfaceImpl> surfaces;
        synchronized (SURFACE_LOCK) {
            surfaces = List.copyOf(SURFACES);
        }

        return surfaces.stream()
                .map(surface -> surface.pick(
                        client.level.dimension(),
                        camera.position(),
                        rayDirection,
                        Math.min(surface.interactionReach(), surface.maxDistance())
                ))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(GrapheneWorldSurfacePick::distance));
    }

    private static void tickPrimaryUse(Minecraft client, Optional<GrapheneWorldSurfacePick> pick) {
        if (!client.options.keyUse.isDown()) {
            pick.filter(GrapheneWorldSurfaceManager::isActivePrimarySurface).ifPresent(currentPick -> activePrimaryPick = currentPick);
            releasePrimaryUse();
            updateHoveredPick(pick);
            return;
        }

        pick.filter(GrapheneWorldSurfaceManager::isActivePrimarySurface).ifPresent(currentPick -> {
            activePrimaryPick = currentPick;
            dispatchPrimaryDrag(currentPick);
        });
    }

    private static boolean isActivePrimarySurface(GrapheneWorldSurfacePick pick) {
        return activePrimaryPick != null && pick.surface() == activePrimaryPick.surface();
    }

    private static void beginPrimaryUse(GrapheneWorldSurfacePick pick) {
        if (hoveredPick != null && hoveredPick.surface() != pick.surface()) {
            clearHoveredPick();
        } else {
            hoveredPick = null;
        }
        activePrimaryPick = pick;
        primaryUsePressed = true;
        dispatchMouseMoved(pick);
        pick.surface().inputAdapter().setFocused(true);
        pick.surface().inputAdapter().mouseClicked(
                WORLD_SURFACE_BROWSER_BUTTON,
                false,
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
    }

    private static void releasePrimaryUse() {
        GrapheneWorldSurfacePick pick = activePrimaryPick;
        primaryUsePressed = false;
        activePrimaryPick = null;
        if (pick == null) {
            return;
        }

        pick.surface().inputAdapter().mouseReleased(
                WORLD_SURFACE_BROWSER_BUTTON,
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
    }

    private static void dispatchPrimaryDrag(GrapheneWorldSurfacePick pick) {
        pick.surface().inputAdapter().mouseDragged(
                WORLD_SURFACE_BROWSER_BUTTON,
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
    }

    private static void updateHoveredPick(Optional<GrapheneWorldSurfacePick> pick) {
        if (pick.isEmpty()) {
            clearHoveredPick();
            return;
        }

        GrapheneWorldSurfacePick currentPick = pick.get();
        if (hoveredPick != null && hoveredPick.surface() != currentPick.surface()) {
            clearHoveredPick();
        }

        hoveredPick = currentPick;
        dispatchMouseMoved(currentPick);
    }

    private static void clearHoveredPick() {
        GrapheneWorldSurfacePick pick = hoveredPick;
        hoveredPick = null;
        if (pick != null) {
            pick.surface().inputAdapter().mouseExited();
        }
    }

    private static void dispatchMouseMoved(GrapheneWorldSurfacePick pick) {
        pick.surface().inputAdapter().mouseMoved(
                pick.surfaceX(),
                pick.surfaceY(),
                pick.renderedWidth(),
                pick.renderedHeight()
        );
    }

    private static void clearSurfaceInputState(GrapheneWorldSurfaceImpl surface) {
        if (activePrimaryPick != null && activePrimaryPick.surface() == surface) {
            releasePrimaryUse();
        }
        if (hoveredPick != null && hoveredPick.surface() == surface) {
            hoveredPick = null;
            surface.inputAdapter().mouseExited();
        }
    }
}
