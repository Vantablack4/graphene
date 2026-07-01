package tytoo.grapheneui.internal.world;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import tytoo.grapheneui.internal.mc.McClient;

import java.util.ArrayList;
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
    private static final int RENDER_CANDIDATE_REFRESH_TICKS = 5;
    private static final int INTERACTION_CANDIDATE_REFRESH_TICKS = 2;
    private static final double CANDIDATE_REFRESH_DISTANCE_SQUARED = 1.0D;
    private static final AtomicInteger NEXT_SURFACE_SEQUENCE = new AtomicInteger();
    private static final AtomicBoolean RENDER_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_TICK_EVENT_REGISTERED = new AtomicBoolean(false);
    private static final Object SURFACE_LOCK = new Object();
    private static final Set<GrapheneWorldSurfaceImpl> SURFACES = new LinkedHashSet<>();
    private static final Map<Object, List<GrapheneWorldSurfaceImpl>> SURFACES_BY_OWNER = new IdentityHashMap<>();
    private static volatile List<GrapheneWorldSurfaceImpl> surfaceSnapshot = List.of();
    private static volatile CandidateCache renderCandidateCache = CandidateCache.empty();
    private static volatile CandidateCache interactionCandidateCache = CandidateCache.empty();
    private static final AtomicInteger SURFACE_STATE_VERSION = new AtomicInteger();
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
        GrapheneWorldSurfacePick nearestPick = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (GrapheneWorldSurfaceImpl surface : surfaceSnapshot) {
            Optional<GrapheneWorldSurfacePick> pick = surface.pick(dimension, rayOrigin, rayDirection, rayLength);
            if (pick.isPresent() && pick.get().distance() < nearestDistance) {
                nearestPick = pick.get();
                nearestDistance = nearestPick.distance();
            }
        }

        return Optional.ofNullable(nearestPick);
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
            refreshSurfaceSnapshotLocked();
        }
        clearSurfaceInputState(surface);
    }

    static void surfaceStateChanged(GrapheneWorldSurfaceImpl surface) {
        Objects.requireNonNull(surface, "surface");
        invalidateCandidateCaches();
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
            refreshSurfaceSnapshotLocked();
        }
    }

    private static void collectSubmits(LevelRenderContext context) {
        Minecraft client = McClient.mc();
        ClientLevel level = client.level;
        Camera camera = context.gameRenderer().getMainCamera();
        if (level == null || camera == null || !camera.isInitialized()) {
            return;
        }

        Vec3 cameraPosition = camera.position();
        boolean screenOpen = McClient.currentScreen() != null;
        List<GrapheneWorldSurfaceImpl> surfaces = renderCandidateSurfaces(
                level.dimension(),
                cameraPosition,
                screenOpen,
                level.getGameTime()
        );
        if (surfaces.isEmpty()) {
            return;
        }

        List<GrapheneWorldSurfaceImpl.RenderCandidate> candidates = new ArrayList<>(surfaces.size());
        for (GrapheneWorldSurfaceImpl surface : surfaces) {
            GrapheneWorldSurfaceImpl.RenderCandidate candidate = surface.collectRenderCandidate(
                    level.dimension(),
                    camera,
                    cameraPosition,
                    screenOpen
            );
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort((left, right) -> Double.compare(right.distanceSquared(), left.distanceSquared()));
        for (GrapheneWorldSurfaceImpl.RenderCandidate candidate : candidates) {
            candidate.surface().submitRenderCandidate(context, candidate);
        }
    }

    private static void tickInputState(Minecraft client) {
        if (!isWorldPointerAvailable(client)) {
            releasePrimaryUse();
            clearHoveredPick();
            return;
        }

        if (primaryUsePressed) {
            Optional<GrapheneWorldSurfacePick> pick = pickActiveForInteractionFromCamera(client);
            tickPrimaryUse(client, pick);
            return;
        }

        Optional<GrapheneWorldSurfacePick> pick = pickNearestForInteractionFromCamera(client);
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
        Vec3 cameraPosition = camera.position();
        Vec3 rayDirection = new Vec3(forward.x(), forward.y(), forward.z());
        GrapheneWorldSurfacePick nearestPick = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (GrapheneWorldSurfaceImpl surface : interactionCandidateSurfaces(client, cameraPosition)) {
            Optional<GrapheneWorldSurfacePick> pick = surface.pickForInteraction(
                    client.level.dimension(),
                    cameraPosition,
                    rayDirection
            );
            if (pick.isPresent() && pick.get().distance() < nearestDistance) {
                nearestPick = pick.get();
                nearestDistance = nearestPick.distance();
            }
        }

        return Optional.ofNullable(nearestPick);
    }

    private static Optional<GrapheneWorldSurfacePick> pickActiveForInteractionFromCamera(Minecraft client) {
        if (activePrimaryPick == null || !(activePrimaryPick.surface() instanceof GrapheneWorldSurfaceImpl surface)) {
            return Optional.empty();
        }

        Camera camera = client.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return Optional.empty();
        }

        Vector3fc forward = camera.forwardVector();
        Vec3 rayDirection = new Vec3(forward.x(), forward.y(), forward.z());
        return surface.pickForInteraction(
                client.level.dimension(),
                camera.position(),
                rayDirection
        );
    }

    private static void refreshSurfaceSnapshotLocked() {
        surfaceSnapshot = List.copyOf(SURFACES);
        invalidateCandidateCaches();
    }

    private static void tickPrimaryUse(Minecraft client, Optional<GrapheneWorldSurfacePick> pick) {
        if (!client.options.keyUse.isDown()) {
            pick.filter(GrapheneWorldSurfaceManager::isActivePrimarySurface).ifPresent(currentPick -> activePrimaryPick = currentPick);
            releasePrimaryUse();
            updateHoveredPick(pick);
            return;
        }

        pick.filter(GrapheneWorldSurfaceManager::isActivePrimarySurface).ifPresent(currentPick -> {
            GrapheneWorldSurfacePick previousPick = activePrimaryPick;
            activePrimaryPick = currentPick;
            if (!sameBrowserPoint(previousPick, currentPick)) {
                dispatchPrimaryDrag(currentPick);
            }
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
                pick.browserPoint()
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
                pick.browserPoint()
        );
    }

    private static void dispatchPrimaryDrag(GrapheneWorldSurfacePick pick) {
        pick.surface().inputAdapter().mouseDragged(
                WORLD_SURFACE_BROWSER_BUTTON,
                pick.browserPoint()
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
        if (sameBrowserPoint(hoveredPick, currentPick)) {
            hoveredPick = currentPick;
            return;
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
                pick.browserPoint()
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

    private static void invalidateCandidateCaches() {
        SURFACE_STATE_VERSION.incrementAndGet();
        renderCandidateCache = CandidateCache.empty();
        interactionCandidateCache = CandidateCache.empty();
    }

    private static boolean sameBrowserPoint(GrapheneWorldSurfacePick previousPick, GrapheneWorldSurfacePick currentPick) {
        return previousPick != null
                && currentPick != null
                && previousPick.surface() == currentPick.surface()
                && previousPick.browserPoint().equals(currentPick.browserPoint());
    }

    private static List<GrapheneWorldSurfaceImpl> renderCandidateSurfaces(
            ResourceKey<Level> dimension,
            Vec3 cameraPosition,
            boolean screenOpen,
            long gameTime
    ) {
        CandidateCache cache = renderCandidateCache;
        int version = SURFACE_STATE_VERSION.get();
        if (cache.matches(version, dimension, screenOpen, cameraPosition, gameTime, RENDER_CANDIDATE_REFRESH_TICKS)) {
            return cache.surfaces();
        }

        List<GrapheneWorldSurfaceImpl> candidates = collectPotentialRenderCandidates(dimension, cameraPosition, screenOpen);
        renderCandidateCache = new CandidateCache(version, dimension, screenOpen, cameraPosition, gameTime, candidates);
        return candidates;
    }

    private static List<GrapheneWorldSurfaceImpl> interactionCandidateSurfaces(Minecraft client, Vec3 cameraPosition) {
        CandidateCache cache = interactionCandidateCache;
        int version = SURFACE_STATE_VERSION.get();
        ResourceKey<Level> dimension = client.level.dimension();
        long gameTime = client.level.getGameTime();
        if (cache.matches(version, dimension, false, cameraPosition, gameTime, INTERACTION_CANDIDATE_REFRESH_TICKS)) {
            return cache.surfaces();
        }

        List<GrapheneWorldSurfaceImpl> candidates = collectPotentialInteractionCandidates(dimension, cameraPosition);
        interactionCandidateCache = new CandidateCache(version, dimension, false, cameraPosition, gameTime, candidates);
        return candidates;
    }

    private static List<GrapheneWorldSurfaceImpl> collectPotentialRenderCandidates(
            ResourceKey<Level> dimension,
            Vec3 cameraPosition,
            boolean screenOpen
    ) {
        List<GrapheneWorldSurfaceImpl> snapshot = surfaceSnapshot;
        if (snapshot.isEmpty()) {
            return List.of();
        }

        List<GrapheneWorldSurfaceImpl> candidates = new ArrayList<>(snapshot.size());
        for (GrapheneWorldSurfaceImpl surface : snapshot) {
            if (surface.isPotentialRenderCandidate(dimension, cameraPosition, screenOpen)) {
                candidates.add(surface);
            }
        }
        return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
    }

    private static List<GrapheneWorldSurfaceImpl> collectPotentialInteractionCandidates(
            ResourceKey<Level> dimension,
            Vec3 cameraPosition
    ) {
        List<GrapheneWorldSurfaceImpl> snapshot = surfaceSnapshot;
        if (snapshot.isEmpty()) {
            return List.of();
        }

        List<GrapheneWorldSurfaceImpl> candidates = new ArrayList<>(snapshot.size());
        for (GrapheneWorldSurfaceImpl surface : snapshot) {
            if (surface.isPotentialInteractionCandidate(dimension, cameraPosition, false)) {
                candidates.add(surface);
            }
        }
        return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
    }

    private record CandidateCache(
            int surfaceVersion,
            ResourceKey<Level> dimension,
            boolean screenOpen,
            Vec3 cameraPosition,
            long gameTime,
            List<GrapheneWorldSurfaceImpl> surfaces
    ) {
        static CandidateCache empty() {
            return new CandidateCache(-1, null, false, Vec3.ZERO, Long.MIN_VALUE, List.of());
        }

        boolean matches(
                int expectedSurfaceVersion,
                ResourceKey<Level> expectedDimension,
                boolean expectedScreenOpen,
                Vec3 expectedCameraPosition,
                long expectedGameTime,
                int refreshTicks
        ) {
            if (surfaceVersion != expectedSurfaceVersion
                    || !Objects.equals(dimension, expectedDimension)
                    || screenOpen != expectedScreenOpen) {
                return false;
            }
            if (expectedGameTime < gameTime || expectedGameTime - gameTime >= refreshTicks) {
                return false;
            }
            return cameraPosition.distanceToSqr(expectedCameraPosition) < CANDIDATE_REFRESH_DISTANCE_SQUARED;
        }
    }
}
