package tytoo.grapheneui.internal.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;
import tytoo.grapheneui.api.surface.BrowserSurfaceTextureFrame;
import tytoo.grapheneui.api.world.GrapheneWorldSurface;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceConfig;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceFacing;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceOrientation;
import tytoo.grapheneui.api.world.GrapheneWorldSurfacePick;
import tytoo.grapheneui.api.world.GrapheneWorldSurfaceSide;
import tytoo.grapheneui.internal.mc.McClient;

import java.awt.Point;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class GrapheneWorldSurfaceImpl implements GrapheneWorldSurface {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final float BILLBOARD_UP_EPSILON = 1.0E-4F;
    private static final double SURFACE_BOUNDS_INFLATE = 0.0625D;

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
    private double interactionReach;
    private boolean renderWhenScreenOpen;
    private long stateVersion;
    private SurfaceGeometry fixedGeometryCache;

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
        this.interactionReach = config.interactionReach();
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
            if (Objects.equals(this.dimension, dimension)) {
                return;
            }

            this.dimension = dimension;
            bumpCandidateState();
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
        Vec3 nextPosition = Objects.requireNonNull(position, "position");
        synchronized (stateLock) {
            if (sameVec3(this.position, nextPosition)) {
                return;
            }

            this.position = nextPosition;
            bumpGeometryState();
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
        float nextWorldWidth = requirePositive(width, "width");
        float nextWorldHeight = requirePositive(height, "height");
        synchronized (stateLock) {
            if (this.worldWidth == nextWorldWidth && this.worldHeight == nextWorldHeight) {
                return;
            }

            this.worldWidth = nextWorldWidth;
            this.worldHeight = nextWorldHeight;
            bumpGeometryState();
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
        Quaternionf nextRotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        synchronized (stateLock) {
            if (this.rotation.equals(nextRotation)) {
                return;
            }

            this.rotation = nextRotation;
            bumpGeometryState();
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
        GrapheneWorldSurfaceFacing nextFacing = Objects.requireNonNull(facing, "facing");
        synchronized (stateLock) {
            if (this.facing == nextFacing) {
                return;
            }

            this.facing = nextFacing;
            bumpGeometryState();
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
        GrapheneWorldSurfaceSide nextSide = Objects.requireNonNull(side, "side");
        synchronized (stateLock) {
            if (this.side == nextSide) {
                return;
            }

            this.side = nextSide;
            bumpCandidateState();
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
        double nextMaxDistance = requirePositive(maxDistance, "maxDistance");
        synchronized (stateLock) {
            if (this.maxDistance == nextMaxDistance) {
                return;
            }

            this.maxDistance = nextMaxDistance;
            bumpCandidateState();
        }
    }

    @Override
    public double interactionReach() {
        synchronized (stateLock) {
            return interactionReach;
        }
    }

    @Override
    public void setInteractionReach(double interactionReach) {
        double nextInteractionReach = requirePositive(interactionReach, "interactionReach");
        synchronized (stateLock) {
            if (this.interactionReach == nextInteractionReach) {
                return;
            }

            this.interactionReach = nextInteractionReach;
            bumpCandidateState();
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
            if (this.renderWhenScreenOpen == renderWhenScreenOpen) {
                return;
            }

            this.renderWhenScreenOpen = renderWhenScreenOpen;
            bumpCandidateState();
        }
    }

    @Override
    public Optional<GrapheneWorldSurfacePick> pickFromRay(
            ResourceKey<Level> dimension,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double rayLength
    ) {
        if (closed.get()) {
            return Optional.empty();
        }

        return pick(snapshotRenderState(), dimension, rayOrigin, rayDirection, rayLength);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        GrapheneWorldSurfaceManager.unregister(this);
        surface.close();
    }

    boolean isPotentialRenderCandidate(ResourceKey<Level> dimension, Vec3 cameraPosition, boolean screenOpen) {
        if (closed.get()) {
            return false;
        }

        return isPotentialRenderCandidate(snapshotRenderState(), dimension, cameraPosition, screenOpen);
    }

    boolean isPotentialInteractionCandidate(ResourceKey<Level> dimension, Vec3 cameraPosition, boolean screenOpen) {
        if (closed.get()) {
            return false;
        }

        RenderState renderState = snapshotRenderState();
        double effectiveReach = Math.min(renderState.interactionReach, renderState.maxDistance);
        return isPotentialCandidate(renderState, dimension, cameraPosition, screenOpen, effectiveReach);
    }

    RenderCandidate collectRenderCandidate(
            ResourceKey<Level> dimension,
            Camera camera,
            Vec3 cameraPosition,
            boolean screenOpen
    ) {
        if (closed.get()) {
            return null;
        }

        RenderState renderState = snapshotRenderState();
        if (!isPotentialRenderCandidate(renderState, dimension, cameraPosition, screenOpen)
                || isBehindCamera(renderState, cameraPosition, camera.forwardVector())) {
            return null;
        }

        double distanceSquared = cameraPosition.distanceToSqr(renderState.position);
        if (distanceSquared > renderState.maxDistance * renderState.maxDistance) {
            return null;
        }

        SurfaceGeometry geometry = resolveGeometry(renderState, cameraPosition);
        QuadSide quadSide = resolveQuadSide(renderState, cameraPosition, geometry);
        if (quadSide == null || !isVisibleToCameraFrustum(camera, geometry)) {
            return null;
        }

        return new RenderCandidate(this, renderState, cameraPosition, distanceSquared, geometry, quadSide);
    }

    void submitRenderCandidate(LevelRenderContext context, RenderCandidate candidate) {
        if (closed.get()) {
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
                    candidate.renderState.position.x - candidate.cameraPosition.x,
                    candidate.renderState.position.y - candidate.cameraPosition.y,
                    candidate.renderState.position.z - candidate.cameraPosition.z
            );
            poseStack.mulPose(new Quaternionf(candidate.geometry.rotation));
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, buffer) -> renderQuad(
                            pose,
                            buffer,
                            textureFrame,
                            candidate.renderState.worldWidth,
                            candidate.renderState.worldHeight,
                            candidate.quadSide
                    )
            );
        } finally {
            poseStack.popPose();
        }
    }

    Optional<GrapheneWorldSurfacePick> pick(
            ResourceKey<Level> dimension,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double rayLength
    ) {
        if (closed.get()) {
            return Optional.empty();
        }

        return pick(snapshotRenderState(), dimension, rayOrigin, rayDirection, rayLength);
    }

    Optional<GrapheneWorldSurfacePick> pickForInteraction(
            ResourceKey<Level> dimension,
            Vec3 rayOrigin,
            Vec3 rayDirection
    ) {
        if (closed.get()) {
            return Optional.empty();
        }

        RenderState renderState = snapshotRenderState();
        return pick(renderState, dimension, rayOrigin, rayDirection, Math.min(renderState.interactionReach, renderState.maxDistance));
    }

    private void bumpGeometryState() {
        stateVersion++;
        fixedGeometryCache = null;
        GrapheneWorldSurfaceManager.surfaceStateChanged(this);
    }

    private void bumpCandidateState() {
        GrapheneWorldSurfaceManager.surfaceStateChanged(this);
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
                    interactionReach,
                    renderWhenScreenOpen,
                    calculateSurfaceRadius(worldWidth, worldHeight),
                    stateVersion
            );
        }
    }

    private static boolean isPotentialRenderCandidate(
            RenderState renderState,
            ResourceKey<Level> dimension,
            Vec3 cameraPosition,
            boolean screenOpen
    ) {
        return isPotentialCandidate(renderState, dimension, cameraPosition, screenOpen, renderState.maxDistance);
    }

    private static boolean isPotentialCandidate(
            RenderState renderState,
            ResourceKey<Level> dimension,
            Vec3 cameraPosition,
            boolean screenOpen,
            double maxDistance
    ) {
        if (!renderState.renderWhenScreenOpen && screenOpen) {
            return false;
        }
        if (renderState.dimension != null && !renderState.dimension.equals(dimension)) {
            return false;
        }

        double effectiveDistance = maxDistance + renderState.surfaceRadius;
        return cameraPosition.distanceToSqr(renderState.position) <= effectiveDistance * effectiveDistance;
    }

    private Optional<GrapheneWorldSurfacePick> pick(
            RenderState renderState,
            ResourceKey<Level> pickDimension,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double rayLength
    ) {
        Objects.requireNonNull(rayOrigin, "rayOrigin");
        Objects.requireNonNull(rayDirection, "rayDirection");
        if (!rayOrigin.isFinite() || !rayDirection.isFinite() || rayDirection.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }
        if (rayLength <= 0.0D || !Double.isFinite(rayLength)) {
            return Optional.empty();
        }
        if (!renderState.renderWhenScreenOpen && McClient.currentScreen() != null) {
            return Optional.empty();
        }
        if (renderState.dimension != null && !renderState.dimension.equals(pickDimension)) {
            return Optional.empty();
        }

        double cameraDistanceSquared = rayOrigin.distanceToSqr(renderState.position);
        if (cameraDistanceSquared > renderState.maxDistance * renderState.maxDistance) {
            return Optional.empty();
        }

        Vec3 normalizedDirection = rayDirection.normalize();
        SurfaceGeometry geometry = resolveGeometry(renderState, rayOrigin);
        QuadSide quadSide = resolveQuadSide(renderState, rayOrigin, geometry);
        if (quadSide == null) {
            return Optional.empty();
        }

        Vector3f normal = geometry.normal;
        double denominator = normalizedDirection.x * normal.x
                + normalizedDirection.y * normal.y
                + normalizedDirection.z * normal.z;
        if (Math.abs(denominator) < 1.0E-6D) {
            return Optional.empty();
        }

        Vec3 originToSurface = renderState.position.subtract(rayOrigin);
        double distance = (originToSurface.x * normal.x + originToSurface.y * normal.y + originToSurface.z * normal.z)
                / denominator;
        if (distance < 0.0D || distance > rayLength) {
            return Optional.empty();
        }

        Vec3 hitPosition = rayOrigin.add(normalizedDirection.scale(distance));
        Vector3f local = new Vector3f(
                (float) (hitPosition.x - renderState.position.x),
                (float) (hitPosition.y - renderState.position.y),
                (float) (hitPosition.z - renderState.position.z)
        );
        geometry.inverseRotation.transform(local);

        float halfWidth = renderState.worldWidth * 0.5F;
        float halfHeight = renderState.worldHeight * 0.5F;
        if (local.x < -halfWidth || local.x > halfWidth || local.y < -halfHeight || local.y > halfHeight) {
            return Optional.empty();
        }

        int renderedWidth = surface.getSurfaceWidth();
        int renderedHeight = surface.getSurfaceHeight();
        boolean frontSide = quadSide == QuadSide.FRONT;
        double u = frontSide
                ? local.x / renderState.worldWidth + 0.5D
                : 0.5D - local.x / renderState.worldWidth;
        double v = 0.5D - local.y / renderState.worldHeight;
        double surfaceX = Math.clamp(u, 0.0D, 1.0D) * renderedWidth;
        double surfaceY = Math.clamp(v, 0.0D, 1.0D) * renderedHeight;
        Point browserPoint = surface.toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight);

        return Optional.of(new GrapheneWorldSurfacePick(
                this,
                hitPosition,
                distance,
                frontSide,
                surfaceX,
                surfaceY,
                renderedWidth,
                renderedHeight,
                browserPoint
        ));
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

    private SurfaceGeometry resolveGeometry(RenderState renderState, Vec3 cameraPosition) {
        if (renderState.facing != GrapheneWorldSurfaceFacing.FIXED) {
            return createGeometry(renderState, resolveRotation(renderState, cameraPosition));
        }

        synchronized (stateLock) {
            if (fixedGeometryCache != null && fixedGeometryCache.stateVersion == renderState.stateVersion) {
                return fixedGeometryCache;
            }

            SurfaceGeometry geometry = createGeometry(renderState, renderState.rotation);
            fixedGeometryCache = geometry;
            return geometry;
        }
    }

    private static SurfaceGeometry createGeometry(RenderState renderState, Quaternionfc rotation) {
        Quaternionf geometryRotation = new Quaternionf(rotation);
        Quaternionf inverseRotation = new Quaternionf(geometryRotation).invert();
        Vector3f localX = geometryRotation.transformPositiveX(new Vector3f());
        Vector3f localY = geometryRotation.transformPositiveY(new Vector3f());
        Vector3f normal = geometryRotation.transformPositiveZ(new Vector3f()).normalize();
        AABB bounds = surfaceBounds(renderState, localX, localY);
        return new SurfaceGeometry(renderState.stateVersion, geometryRotation, inverseRotation, normal, bounds);
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

    private static QuadSide resolveQuadSide(RenderState renderState, Vec3 cameraPosition, SurfaceGeometry geometry) {
        boolean cameraInFront = cameraInFrontOfSurface(renderState.position, cameraPosition, geometry.normal);
        return switch (renderState.side) {
            case FRONT_ONLY -> cameraInFront ? QuadSide.FRONT : null;
            case BACK_ONLY -> cameraInFront ? null : QuadSide.BACK;
            case DOUBLE_SIDED_MIRRORED, DOUBLE_SIDED_READABLE -> cameraInFront ? QuadSide.FRONT : QuadSide.BACK;
        };
    }

    private static boolean cameraInFrontOfSurface(Vec3 position, Vec3 cameraPosition, Vector3f normal) {
        double deltaX = cameraPosition.x - position.x;
        double deltaY = cameraPosition.y - position.y;
        double deltaZ = cameraPosition.z - position.z;
        return deltaX * normal.x + deltaY * normal.y + deltaZ * normal.z >= 0.0D;
    }

    private static boolean isBehindCamera(RenderState renderState, Vec3 cameraPosition, Vector3fc cameraForward) {
        if (cameraForward == null) {
            return false;
        }

        double deltaX = renderState.position.x - cameraPosition.x;
        double deltaY = renderState.position.y - cameraPosition.y;
        double deltaZ = renderState.position.z - cameraPosition.z;
        double forwardDistance = deltaX * cameraForward.x() + deltaY * cameraForward.y() + deltaZ * cameraForward.z();
        return forwardDistance < -renderState.surfaceRadius;
    }

    private static boolean isVisibleToCameraFrustum(Camera camera, SurfaceGeometry geometry) {
        Frustum frustum = camera.getCullFrustum();
        return frustum == null || frustum.isVisible(geometry.bounds);
    }

    private static AABB surfaceBounds(RenderState renderState, Vector3f localX, Vector3f localY) {
        float halfWidth = renderState.worldWidth * 0.5F;
        float halfHeight = renderState.worldHeight * 0.5F;
        double extentX = Math.abs(localX.x) * halfWidth + Math.abs(localY.x) * halfHeight;
        double extentY = Math.abs(localX.y) * halfWidth + Math.abs(localY.y) * halfHeight;
        double extentZ = Math.abs(localX.z) * halfWidth + Math.abs(localY.z) * halfHeight;

        return new AABB(
                renderState.position.x - extentX,
                renderState.position.y - extentY,
                renderState.position.z - extentZ,
                renderState.position.x + extentX,
                renderState.position.y + extentY,
                renderState.position.z + extentZ
        ).inflate(SURFACE_BOUNDS_INFLATE);
    }

    private static double calculateSurfaceRadius(float worldWidth, float worldHeight) {
        double halfWidth = worldWidth * 0.5D;
        double halfHeight = worldHeight * 0.5D;
        return Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight) + SURFACE_BOUNDS_INFLATE;
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

    private static boolean sameVec3(Vec3 left, Vec3 right) {
        return left != null
                && right != null
                && Double.doubleToLongBits(left.x) == Double.doubleToLongBits(right.x)
                && Double.doubleToLongBits(left.y) == Double.doubleToLongBits(right.y)
                && Double.doubleToLongBits(left.z) == Double.doubleToLongBits(right.z);
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
            double interactionReach,
            boolean renderWhenScreenOpen,
            double surfaceRadius,
            long stateVersion
    ) {
    }

    record SurfaceGeometry(
            long stateVersion,
            Quaternionf rotation,
            Quaternionf inverseRotation,
            Vector3f normal,
            AABB bounds
    ) {
    }

    record RenderCandidate(
            GrapheneWorldSurfaceImpl surface,
            RenderState renderState,
            Vec3 cameraPosition,
            double distanceSquared,
            SurfaceGeometry geometry,
            QuadSide quadSide
    ) {
    }

    private enum QuadSide {
        FRONT,
        BACK
    }
}
