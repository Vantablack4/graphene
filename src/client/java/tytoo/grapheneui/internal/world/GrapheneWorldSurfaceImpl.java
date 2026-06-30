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
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;
import tytoo.grapheneui.api.surface.BrowserSurfaceTextureFrame;
import tytoo.grapheneui.api.world.GrapheneWorldSurface;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceConfig;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceFacing;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceOrientation;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceSide;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class GrapheneWorldSurfaceImpl implements GrapheneWorldSurface {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final float BILLBOARD_UP_EPSILON = 1.0E-4F;

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
    private GrapheneWorldSurfaceSide side;
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
        this.side = config.side();
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
    public GrapheneWorldSurfaceOrientation orientation() {
        synchronized (stateLock) {
            return GrapheneWorldSurfaceOrientation.custom(rotation);
        }
    }

    @Override
    public void setRotation(Quaternionfc rotation) {
        synchronized (stateLock) {
            this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        }
    }

    @Override
    public void setOrientation(GrapheneWorldSurfaceOrientation orientation) {
        setRotation(Objects.requireNonNull(orientation, "orientation").rotation());
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
    public GrapheneWorldSurfaceSide side() {
        synchronized (stateLock) {
            return side;
        }
    }

    @Override
    public void setSide(GrapheneWorldSurfaceSide side) {
        synchronized (stateLock) {
            this.side = Objects.requireNonNull(side, "side");
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

        Quaternionf effectiveRotation = resolveRotation(renderState, cameraPosition);
        QuadSide quadSide = resolveQuadSide(renderState, cameraPosition, effectiveRotation);
        if (quadSide == null) {
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
            poseStack.mulPose(effectiveRotation);
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, buffer) -> renderQuad(
                            pose,
                            buffer,
                            textureFrame,
                            renderState.worldWidth,
                            renderState.worldHeight,
                            quadSide
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
                    side,
                    maxDistance,
                    renderWhenScreenOpen
            );
        }
    }

    private static Quaternionf resolveRotation(RenderState renderState, Vec3 cameraPosition) {
        if (renderState.facing == GrapheneWorldSurfaceFacing.CAMERA) {
            return faceCameraRotation(renderState.position, cameraPosition);
        }
        if (renderState.facing == GrapheneWorldSurfaceFacing.CAMERA_YAW) {
            return faceCameraYawRotation(renderState.position, cameraPosition);
        }

        return new Quaternionf(renderState.rotation);
    }

    private static Quaternionf faceCameraRotation(Vec3 position, Vec3 cameraPosition) {
        Vec3 toCamera = cameraPosition.subtract(position);
        if (!toCamera.isFinite() || toCamera.lengthSqr() < 1.0E-6D) {
            return new Quaternionf();
        }

        Vector3f forward = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z).normalize();
        Vector3f up = projectedUp(forward, 0.0F, 1.0F, 0.0F);
        if (up.lengthSquared() < BILLBOARD_UP_EPSILON) {
            up = projectedUp(forward, 0.0F, 0.0F, 1.0F);
        }

        up.normalize();
        Vector3f right = up.cross(forward, new Vector3f()).normalize();

        return new Matrix3f(right, up, forward).getNormalizedRotation(new Quaternionf());
    }

    private static Vector3f projectedUp(Vector3f forward, float x, float y, float z) {
        float dot = forward.dot(x, y, z);
        return new Vector3f(
                x - forward.x * dot,
                y - forward.y * dot,
                z - forward.z * dot
        );
    }

    private static Quaternionf faceCameraYawRotation(Vec3 position, Vec3 cameraPosition) {
        double deltaX = cameraPosition.x - position.x;
        double deltaZ = cameraPosition.z - position.z;
        double horizontalLengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (horizontalLengthSquared < 1.0E-6D) {
            return new Quaternionf();
        }

        return new Quaternionf().rotationY((float) Math.atan2(deltaX, deltaZ));
    }

    private static QuadSide resolveQuadSide(RenderState renderState, Vec3 cameraPosition, Quaternionf effectiveRotation) {
        boolean cameraInFront = cameraInFrontOfSurface(renderState.position, cameraPosition, effectiveRotation);
        return switch (renderState.side) {
            case FRONT_ONLY -> cameraInFront ? QuadSide.FRONT : null;
            case BACK_ONLY -> cameraInFront ? null : QuadSide.BACK;
            case DOUBLE_SIDED_READABLE -> cameraInFront ? QuadSide.FRONT : QuadSide.BACK;
        };
    }

    private static boolean cameraInFrontOfSurface(Vec3 position, Vec3 cameraPosition, Quaternionf effectiveRotation) {
        Vector3f normal = effectiveRotation.transformPositiveZ(new Vector3f());
        double deltaX = cameraPosition.x - position.x;
        double deltaY = cameraPosition.y - position.y;
        double deltaZ = cameraPosition.z - position.z;
        return deltaX * normal.x + deltaY * normal.y + deltaZ * normal.z >= 0.0D;
    }

    private void renderQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            BrowserSurfaceTextureFrame frame,
            float width,
            float height,
            QuadSide side
    ) {
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        if (side == QuadSide.FRONT) {
            vertex(buffer, pose, -halfWidth, halfHeight, 0.0F, frame.u0(), frame.v0(), 1.0F);
            vertex(buffer, pose, -halfWidth, -halfHeight, 0.0F, frame.u0(), frame.v1(), 1.0F);
            vertex(buffer, pose, halfWidth, -halfHeight, 0.0F, frame.u1(), frame.v1(), 1.0F);
            vertex(buffer, pose, halfWidth, halfHeight, 0.0F, frame.u1(), frame.v0(), 1.0F);
            return;
        }

        vertex(buffer, pose, halfWidth, halfHeight, 0.0F, frame.u0(), frame.v0(), -1.0F);
        vertex(buffer, pose, halfWidth, -halfHeight, 0.0F, frame.u0(), frame.v1(), -1.0F);
        vertex(buffer, pose, -halfWidth, -halfHeight, 0.0F, frame.u1(), frame.v1(), -1.0F);
        vertex(buffer, pose, -halfWidth, halfHeight, 0.0F, frame.u1(), frame.v0(), -1.0F);
    }

    private void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(WHITE)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, 0.0F, 0.0F, normalZ);
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
            GrapheneWorldSurfaceSide side,
            double maxDistance,
            boolean renderWhenScreenOpen
    ) {
    }

    private enum QuadSide {
        FRONT,
        BACK
    }
}
