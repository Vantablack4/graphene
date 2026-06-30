package tytoo.grapheneui.internal.world;

import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;
import tytoo.grapheneui.api.surface.GrapheneLoadListener;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;
import tytoo.grapheneui.api.world.GrapheneWorldAnchor;
import tytoo.grapheneui.api.world.GrapheneWorldAnchorOcclusion;
import tytoo.grapheneui.api.world.GrapheneWorldOverlayConfig;
import tytoo.grapheneui.api.world.GrapheneWorldOverlayLayer;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class GrapheneWorldOverlayLayerImpl implements GrapheneWorldOverlayLayer, HudElement {
    private static final double NDC_EDGE = 1.0D;
    private static final double HIDDEN_DEPTH = 1.0D;
    private static final double OCCLUSION_EPSILON_SQUARED = 0.0625D;
    private static final long NO_OCCLUSION_SAMPLE = Long.MIN_VALUE;

    private final Object lock = new Object();
    private final Identifier elementId;
    private final GrapheneWorldOverlayConfig config;
    private final BrowserSurface surface;
    private final BrowserSurfaceInputAdapter inputAdapter;
    private final LinkedHashMap<String, GrapheneWorldAnchor> anchors = new LinkedHashMap<>();
    private final Map<String, OcclusionState> occlusionStates = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong frameSequence = new AtomicLong();
    private int visibleAnchorCount;

    GrapheneWorldOverlayLayerImpl(Identifier elementId, GrapheneWorldOverlayConfig config) {
        this.elementId = Objects.requireNonNull(elementId, "elementId");
        this.config = Objects.requireNonNull(config, "config");
        this.surface = BrowserSurface.builder()
                .url(this.config.url())
                .transparent(true)
                .surfaceSize(1, 1)
                .autoResolution()
                .config(this.config.surfaceConfig())
                .owner(this)
                .build();
        this.inputAdapter = new BrowserSurfaceInputAdapter(this.surface);
        this.surface.addLoadListener(new GrapheneLoadListener() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                emitResetOnMainThread();
            }
        });
    }

    @Override
    public Identifier elementId() {
        return elementId;
    }

    @Override
    public BrowserSurface surface() {
        return surface;
    }

    @Override
    public GrapheneBridge bridge() {
        return surface.bridge();
    }

    @Override
    public GrapheneNativeSlots nativeSlots() {
        return surface.nativeSlots();
    }

    @Override
    public BrowserSurfaceInputAdapter inputAdapter() {
        return inputAdapter;
    }

    @Override
    public void updateAnchors(Collection<GrapheneWorldAnchor> anchors) {
        Objects.requireNonNull(anchors, "anchors");
        ensureOpen();
        synchronized (lock) {
            this.anchors.clear();
            for (GrapheneWorldAnchor anchor : anchors) {
                this.anchors.put(anchor.id(), anchor);
            }
            retainOcclusionStatesForAnchorsLocked();
        }
    }

    @Override
    public void upsertAnchor(GrapheneWorldAnchor anchor) {
        Objects.requireNonNull(anchor, "anchor");
        ensureOpen();
        synchronized (lock) {
            anchors.put(anchor.id(), anchor);
        }
    }

    @Override
    public void removeAnchor(String id) {
        Objects.requireNonNull(id, "id");
        ensureOpen();
        synchronized (lock) {
            anchors.remove(id);
            occlusionStates.remove(id);
        }
    }

    @Override
    public void clearAnchors() {
        ensureOpen();
        synchronized (lock) {
            anchors.clear();
            occlusionStates.clear();
            visibleAnchorCount = 0;
        }
        emitResetOnMainThread();
    }

    @Override
    public int anchorCount() {
        synchronized (lock) {
            return anchors.size();
        }
    }

    @Override
    public int visibleAnchorCount() {
        synchronized (lock) {
            return visibleAnchorCount;
        }
    }

    @Override
    public boolean hasVisibleAnchors() {
        return visibleAnchorCount() > 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (closed.get()) {
            return;
        }

        List<GrapheneWorldAnchor> anchorSnapshot = anchorsSnapshot();
        if (anchorSnapshot.isEmpty()) {
            return;
        }

        Minecraft minecraft = McClient.mc();
        if (!config.renderWhenScreenOpen() && McClient.currentScreen() != null) {
            updateVisibleCount(0);
            return;
        }

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        surface.setSurfaceSize(guiWidth, guiHeight);

        FramePayload framePayload = buildFramePayload(minecraft, deltaTracker, guiWidth, guiHeight, anchorSnapshot);
        updateVisibleCount(framePayload.anchors.size());
        if (bridge().isReady()) {
            bridge().emitJson(GrapheneWorldOverlayChannels.FRAME, framePayload);
        }
        if (framePayload.anchors.isEmpty()) {
            return;
        }

        surface.render(graphics, 0, 0, guiWidth, guiHeight);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        synchronized (lock) {
            anchors.clear();
            occlusionStates.clear();
            visibleAnchorCount = 0;
        }
        HudElementRegistry.removeElement(elementId);
        GrapheneWorldOverlayManager.unregister(this);
        surface.close();
    }

    private FramePayload buildFramePayload(
            Minecraft minecraft,
            DeltaTracker deltaTracker,
            int guiWidth,
            int guiHeight,
            List<GrapheneWorldAnchor> anchorSnapshot
    ) {
        ClientLevel level = minecraft.level;
        GameRenderer gameRenderer = minecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        long sequence = frameSequence.incrementAndGet();

        if (level == null || minecraft.player == null || camera == null || !camera.isInitialized()) {
            return new FramePayload(sequence, guiWidth, guiHeight, partialTick, null, List.of());
        }

        Vec3 cameraPosition = camera.position();
        Frustum frustum = camera.getCullFrustum();
        ArrayList<ProjectedAnchor> projectedAnchors = new ArrayList<>(Math.min(anchorSnapshot.size(), config.maxAnchors()));

        for (GrapheneWorldAnchor anchor : anchorSnapshot) {
            ProjectedAnchor projectedAnchor = projectAnchor(
                    gameRenderer,
                    level,
                    cameraPosition,
                    frustum,
                    guiWidth,
                    guiHeight,
                    anchor
            );
            if (projectedAnchor != null) {
                projectedAnchors.add(projectedAnchor);
            }
        }

        projectedAnchors.sort(Comparator
                .comparingInt((ProjectedAnchor projectedAnchor) -> projectedAnchor.priority).reversed()
                .thenComparingDouble(projectedAnchor -> projectedAnchor.distance)
                .thenComparing(projectedAnchor -> projectedAnchor.id));

        int limit = Math.min(projectedAnchors.size(), config.maxAnchors());
        ArrayList<AnchorPayload> anchorPayloads = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            anchorPayloads.add(projectedAnchors.get(index).toPayload());
        }

        CameraPayload cameraPayload = new CameraPayload(
                level.dimension().identifier().toString(),
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z
        );
        return new FramePayload(sequence, guiWidth, guiHeight, partialTick, cameraPayload, anchorPayloads);
    }

    private ProjectedAnchor projectAnchor(
            GameRenderer gameRenderer,
            ClientLevel level,
            Vec3 cameraPosition,
            Frustum frustum,
            int guiWidth,
            int guiHeight,
            GrapheneWorldAnchor anchor
    ) {
        if (anchor.dimension() != null && !anchor.dimension().equals(level.dimension())) {
            return null;
        }

        Vec3 position = anchor.position();
        if (!position.isFinite()) {
            return null;
        }

        double maxDistance = anchor.maxDistance() == GrapheneWorldAnchor.DEFAULT_MAX_DISTANCE
                ? config.defaultMaxDistance()
                : anchor.maxDistance();
        double distanceSquared = cameraPosition.distanceToSqr(position);
        if (distanceSquared > maxDistance * maxDistance) {
            return null;
        }

        if (!isInFrustum(frustum, anchor, position)) {
            return null;
        }

        if (!isOcclusionVisible(level, cameraPosition, distanceSquared, anchor)) {
            return null;
        }

        Vec3 projected = gameRenderer.projectPointToScreen(position);
        double projectedX = projected.x;
        double projectedY = projected.y;
        double projectedZ = projected.z;
        if (!Double.isFinite(projectedX) || !Double.isFinite(projectedY) || !Double.isFinite(projectedZ)) {
            return null;
        }

        if (projectedZ > HIDDEN_DEPTH) {
            return null;
        }

        double guiX = (projectedX + NDC_EDGE) * 0.5D * guiWidth + anchor.screenOffsetX();
        double guiY = (NDC_EDGE - projectedY) * 0.5D * guiHeight + anchor.screenOffsetY();
        int screenMargin = config.screenMargin();
        if (guiX < -screenMargin || guiX > guiWidth + screenMargin || guiY < -screenMargin || guiY > guiHeight + screenMargin) {
            return null;
        }

        return new ProjectedAnchor(
                anchor.id(),
                anchor.kind(),
                anchor.priority(),
                anchor.interactive(),
                position,
                guiX,
                guiY,
                projectedZ,
                Math.sqrt(distanceSquared),
                anchor.payload()
        );
    }

    private boolean isInFrustum(Frustum frustum, GrapheneWorldAnchor anchor, Vec3 position) {
        if (frustum == null) {
            return true;
        }

        AABB bounds = anchor.bounds();
        if (bounds != null) {
            return frustum.isVisible(bounds);
        }

        return frustum.pointInFrustum(position.x, position.y, position.z);
    }

    private boolean isOcclusionVisible(
            ClientLevel level,
            Vec3 cameraPosition,
            double distanceSquared,
            GrapheneWorldAnchor anchor
    ) {
        GrapheneWorldAnchorOcclusion occlusion = anchor.occlusion();
        if (occlusion == GrapheneWorldAnchorOcclusion.NONE) {
            return true;
        }

        long gameTime = level.getGameTime();
        OcclusionState occlusionState = occlusionStates.computeIfAbsent(anchor.id(), ignored -> new OcclusionState());
        if (occlusion == GrapheneWorldAnchorOcclusion.THROTTLED_RAYCAST
                && occlusionState.gameTime != NO_OCCLUSION_SAMPLE
                && gameTime - occlusionState.gameTime < config.occlusionIntervalTicks()) {
            return occlusionState.visible;
        }

        boolean visible = raycastVisible(level, cameraPosition, distanceSquared, anchor.position());
        occlusionState.gameTime = gameTime;
        occlusionState.visible = visible;
        return visible;
    }

    private boolean raycastVisible(Level level, Vec3 cameraPosition, double distanceSquared, Vec3 position) {
        HitResult hitResult = level.clip(new ClipContext(
                cameraPosition,
                position,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                McClient.mc().player
        ));
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        double hitDistanceSquared = cameraPosition.distanceToSqr(hitResult.getLocation());
        return hitDistanceSquared + OCCLUSION_EPSILON_SQUARED >= distanceSquared;
    }

    private List<GrapheneWorldAnchor> anchorsSnapshot() {
        synchronized (lock) {
            return List.copyOf(anchors.values());
        }
    }

    private void updateVisibleCount(int visibleAnchorCount) {
        synchronized (lock) {
            this.visibleAnchorCount = visibleAnchorCount;
        }
    }

    private void retainOcclusionStatesForAnchorsLocked() {
        occlusionStates.keySet().retainAll(anchors.keySet());
    }

    private void emitReset() {
        if (closed.get()) {
            return;
        }

        bridge().emitJson(GrapheneWorldOverlayChannels.RESET, new ResetPayload(frameSequence.incrementAndGet()));
    }

    private void emitResetOnMainThread() {
        McClient.runOnMainThread(this::emitReset);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("World overlay layer is closed");
        }
    }

    private static final class OcclusionState {
        private long gameTime = NO_OCCLUSION_SAMPLE;
        private boolean visible = true;
    }

    private record ProjectedAnchor(
            String id,
            String kind,
            int priority,
            boolean interactive,
            Vec3 position,
            double x,
            double y,
            double depth,
            double distance,
            JsonElement payload
    ) {
        private AnchorPayload toPayload() {
            return new AnchorPayload(
                    id,
                    kind,
                    priority,
                    interactive,
                    x,
                    y,
                    depth,
                    distance,
                    1.0D,
                    1.0D,
                    position.x,
                    position.y,
                    position.z,
                    payload
            );
        }
    }

    private record FramePayload(
            int version,
            long sequence,
            int guiWidth,
            int guiHeight,
            float partialTick,
            CameraPayload camera,
            List<AnchorPayload> anchors
    ) {
        private FramePayload(
                long sequence,
                int guiWidth,
                int guiHeight,
                float partialTick,
                CameraPayload camera,
                List<AnchorPayload> anchors
        ) {
            this(
                    GrapheneWorldOverlayChannels.VERSION,
                    sequence,
                    guiWidth,
                    guiHeight,
                    partialTick,
                    camera,
                    anchors
            );
        }
    }

    private record CameraPayload(
            String dimension,
            double x,
            double y,
            double z
    ) {
    }

    private record AnchorPayload(
            String id,
            String kind,
            int priority,
            boolean interactive,
            double x,
            double y,
            double depth,
            double distance,
            double scale,
            double alpha,
            double worldX,
            double worldY,
            double worldZ,
            JsonElement payload
    ) {
    }

    private record ResetPayload(
            int version,
            long sequence
    ) {
        private ResetPayload(long sequence) {
            this(GrapheneWorldOverlayChannels.VERSION, sequence);
        }
    }
}
