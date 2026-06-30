package tytoo.grapheneui.internal.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;
import tytoo.grapheneui.api.surface.BrowserSurfaceTextureFrame;
import tytoo.grapheneui.api.world.GrapheneWorldSurface;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceConfig;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceFacing;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class GrapheneWorldSurfaceImpl implements GrapheneWorldSurface {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int WHITE = 0xFFFFFFFF;

    private final Object stateLock = new Object();
    private final Identifier surfaceId;
    private final BrowserSurface surface;
    private final BrowserSurfaceInputAdapter inputAdapter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ResourceKey<Level> dimension;
    private Vec3 position;
    private float worldWidth;
    private float worldHeight;
    private Quaternionf rotation;
    private GrapheneWorldSurfaceFacing facing;
    private double maxDistance;
    private boolean renderWhenScreenOpen;

    GrapheneWorldSurfaceImpl(Identifier surfaceId, GrapheneWorldSurfaceConfig config) {
        this.surfaceId = Objects.requireNonNull(surfaceId, "surfaceId");
        this.surface = BrowserSurface.builder()
                .url(config.url())
                .transparent(true)
                .surfaceSize(config.surfaceWidth(), config.surfaceHeight())
                .resolution(config.resolutionWidth(), config.resolutionHeight())
                .config(config.surfaceConfig())
                .owner(this)
                .build();
        this.inputAdapter = new BrowserSurfaceInputAdapter(this.surface);
        this.dimension = config.dimension();
        this.position = config.position();
        this.worldWidth = config.worldWidth();
        this.worldHeight = config.worldHeight();
        this.rotation = config.rotation();
        this.facing = config.facing();
        this.maxDistance = config.maxDistance();
        this.renderWhenScreenOpen = config.renderWhenScreenOpen();
    }

    @Override
    public Identifier surfaceId() {
        return surfaceId;
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
    public ResourceKey<Level> dimension() {
        synchronized (stateLock) {
            return dimension;
        }
    }

    @Override
    public void setDimension(ResourceKey<Level> dimension) {
        synchronized (stateLock) {
            this.dimension = dimension;
        }
    }

    @Override
    public Vec3 position() {
        synchronized (stateLock) {
            return position;
        }
    }

    @Override
    public void setPosition(Vec3 position) {
        synchronized (stateLock) {
            this.position = Objects.requireNonNull(position, "position");
        }
    }

    @Override
    public float worldWidth() {
        synchronized (stateLock) {
            return worldWidth;
        }
    }

    @Override
    public float worldHeight() {
        synchronized (stateLock) {
            return worldHeight;
        }
    }

    @Override
    public void setWorldSize(float width, float height) {
        synchronized (stateLock) {
            this.worldWidth = requirePositive(width, "width");
            this.worldHeight = requirePositive(height, "height");
        }
    }

    @Override
    public Quaternionf rotation() {
        synchronized (stateLock) {
            return new Quaternionf(rotation);
        }
    }

    @Override
    public void setRotation(Quaternionfc rotation) {
        synchronized (stateLock) {
            this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        }
    }

    @Override
    public void setRotationDegrees(float pitch, float yaw, float roll) {
        setRotation(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
        ));
    }

    @Override
    public GrapheneWorldSurfaceFacing facing() {
        synchronized (stateLock) {
            return facing;
        }
    }

    @Override
    public void setFacing(GrapheneWorldSurfaceFacing facing) {
        synchronized (stateLock) {
            this.facing = Objects.requireNonNull(facing, "facing");
        }
    }

    @Override
    public double maxDistance() {
        synchronized (stateLock) {
            return maxDistance;
        }
    }

    @Override
    public void setMaxDistance(double maxDistance) {
        synchronized (stateLock) {
            this.maxDistance = requirePositive(maxDistance, "maxDistance");
        }
    }

    @Override
    public boolean renderWhenScreenOpen() {
        synchronized (stateLock) {
            return renderWhenScreenOpen;
        }
    }

    @Override
    public void setRenderWhenScreenOpen(boolean renderWhenScreenOpen) {
        synchronized (stateLock) {
            this.renderWhenScreenOpen = renderWhenScreenOpen;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        GrapheneWorldSurfaceManager.unregister(this);
        surface.close();
    }

    void collectSubmit(LevelRenderContext context) {
        if (closed.get()) {
            return;
        }

        RenderState renderState = snapshotRenderState();
        if (!renderState.renderWhenScreenOpen && McClient.currentScreen() != null) {
            return;
        }

        ClientLevel level = McClient.mc().level;
        Camera camera = context.gameRenderer().getMainCamera();
        if (level == null || camera == null || !camera.isInitialized()) {
            return;
        }

        if (renderState.dimension != null && !renderState.dimension.equals(level.dimension())) {
            return;
        }

        Vec3 cameraPosition = camera.position();
        double distanceSquared = cameraPosition.distanceToSqr(renderState.position);
        if (distanceSquared > renderState.maxDistance * renderState.maxDistance) {
            return;
        }

        BrowserSurfaceTextureFrame textureFrame = surface.prepareTextureFrame();
        if (textureFrame == null) {
            return;
        }

        RenderType renderType = RenderTypes.entityTranslucent(textureFrame.textureId(), false);
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(
                    renderState.position.x - cameraPosition.x,
                    renderState.position.y - cameraPosition.y,
                    renderState.position.z - cameraPosition.z
            );
            if (renderState.facing == GrapheneWorldSurfaceFacing.CAMERA) {
                poseStack.mulPose(camera.rotation());
            } else {
                poseStack.mulPose(renderState.rotation);
            }
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, buffer) -> renderQuad(
                            pose,
                            buffer,
                            textureFrame,
                            renderState.worldWidth,
                            renderState.worldHeight
                    )
            );
        } finally {
            poseStack.popPose();
        }
    }

    private RenderState snapshotRenderState() {
        synchronized (stateLock) {
            return new RenderState(
                    dimension,
                    position,
                    worldWidth,
                    worldHeight,
                    new Quaternionf(rotation),
                    facing,
                    maxDistance,
                    renderWhenScreenOpen
            );
        }
    }

    private void renderQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            BrowserSurfaceTextureFrame frame,
            float width,
            float height
    ) {
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        vertex(buffer, pose, -halfWidth, halfHeight, 0.0F, frame.u0(), frame.v0());
        vertex(buffer, pose, -halfWidth, -halfHeight, 0.0F, frame.u0(), frame.v1());
        vertex(buffer, pose, halfWidth, -halfHeight, 0.0F, frame.u1(), frame.v1());
        vertex(buffer, pose, halfWidth, halfHeight, 0.0F, frame.u1(), frame.v0());
    }

    private void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z)
                .setColor(WHITE)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static float requirePositive(float value, String name) {
        if (value <= 0.0F || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }

        return value;
    }

    private static double requirePositive(double value, String name) {
        if (value <= 0.0D || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }

        return value;
    }

    private record RenderState(
            ResourceKey<Level> dimension,
            Vec3 position,
            float worldWidth,
            float worldHeight,
            Quaternionf rotation,
            GrapheneWorldSurfaceFacing facing,
            double maxDistance,
            boolean renderWhenScreenOpen
    ) {
    }
}
