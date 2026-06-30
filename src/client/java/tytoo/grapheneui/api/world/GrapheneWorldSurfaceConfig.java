package tytoo.grapheneui.api.world;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import tytoo.grapheneui.api.surface.BrowserSurfaceConfig;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldSurfaceConfig {
    public static final int DEFAULT_SURFACE_WIDTH = 512;
    public static final int DEFAULT_SURFACE_HEIGHT = 256;
    public static final int DEFAULT_MAX_FPS = 30;
    public static final double DEFAULT_MAX_DISTANCE = 48.0D;
    public static final double DEFAULT_INTERACTION_REACH = 64.0D;
    public static final float DEFAULT_WORLD_WIDTH = 2.0F;
    public static final float DEFAULT_WORLD_HEIGHT = 1.0F;

    private final String url;
    private final Identifier surfaceId;
    private final Object owner;
    private final BrowserSurfaceConfig surfaceConfig;
    private final int surfaceWidth;
    private final int surfaceHeight;
    private final int resolutionWidth;
    private final int resolutionHeight;
    private final int maxFps;
    private final ResourceKey<Level> dimension;
    private final Vec3 position;
    private final float worldWidth;
    private final float worldHeight;
    private final Quaternionf rotation;
    private final GrapheneWorldSurfaceFacing facing;
    private final GrapheneWorldSurfaceSide side;
    private final double maxDistance;
    private final double interactionReach;
    private final boolean renderWhenScreenOpen;

    private GrapheneWorldSurfaceConfig(Builder builder) {
        this.url = requireNonBlank(builder.url, "url");
        this.surfaceId = builder.surfaceId;
        this.owner = builder.owner;
        this.surfaceConfig = Objects.requireNonNull(builder.surfaceConfig, "surfaceConfig").withMaxFps(builder.maxFps);
        this.surfaceWidth = builder.surfaceWidth;
        this.surfaceHeight = builder.surfaceHeight;
        this.resolutionWidth = builder.resolutionWidth;
        this.resolutionHeight = builder.resolutionHeight;
        this.maxFps = builder.maxFps;
        this.dimension = builder.dimension;
        this.position = Objects.requireNonNull(builder.position, "position");
        this.worldWidth = builder.worldWidth;
        this.worldHeight = builder.worldHeight;
        this.rotation = new Quaternionf(builder.rotation);
        this.facing = Objects.requireNonNull(builder.facing, "facing");
        this.side = Objects.requireNonNull(builder.side, "side");
        this.maxDistance = builder.maxDistance;
        this.interactionReach = builder.interactionReach;
        this.renderWhenScreenOpen = builder.renderWhenScreenOpen;
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    public String url() {
        return url;
    }

    public Identifier surfaceId() {
        return surfaceId;
    }

    public Object owner() {
        return owner;
    }

    public BrowserSurfaceConfig surfaceConfig() {
        return surfaceConfig;
    }

    public int surfaceWidth() {
        return surfaceWidth;
    }

    public int surfaceHeight() {
        return surfaceHeight;
    }

    public int resolutionWidth() {
        return resolutionWidth;
    }

    public int resolutionHeight() {
        return resolutionHeight;
    }

    public int maxFps() {
        return maxFps;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Vec3 position() {
        return position;
    }

    public float worldWidth() {
        return worldWidth;
    }

    public float worldHeight() {
        return worldHeight;
    }

    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    public GrapheneWorldSurfaceOrientation orientation() {
        return GrapheneWorldSurfaceOrientation.custom(rotation);
    }

    public GrapheneWorldSurfaceFacing facing() {
        return facing;
    }

    public GrapheneWorldSurfaceSide side() {
        return side;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public double interactionReach() {
        return interactionReach;
    }

    public boolean renderWhenScreenOpen() {
        return renderWhenScreenOpen;
    }

    private static String requireNonBlank(String value, String name) {
        String normalizedValue = Objects.requireNonNull(value, name).trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return normalizedValue;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }

        return value;
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

    public static final class Builder {
        private String url;
        private Identifier surfaceId;
        private Object owner;
        private BrowserSurfaceConfig surfaceConfig = BrowserSurfaceConfig.defaults();
        private int surfaceWidth = DEFAULT_SURFACE_WIDTH;
        private int surfaceHeight = DEFAULT_SURFACE_HEIGHT;
        private int resolutionWidth = DEFAULT_SURFACE_WIDTH;
        private int resolutionHeight = DEFAULT_SURFACE_HEIGHT;
        private int maxFps = DEFAULT_MAX_FPS;
        private ResourceKey<Level> dimension;
        private Vec3 position = Vec3.ZERO;
        private float worldWidth = DEFAULT_WORLD_WIDTH;
        private float worldHeight = DEFAULT_WORLD_HEIGHT;
        private Quaternionf rotation = new Quaternionf();
        private GrapheneWorldSurfaceFacing facing = GrapheneWorldSurfaceFacing.FIXED;
        private GrapheneWorldSurfaceSide side = GrapheneWorldSurfaceSide.DOUBLE_SIDED_READABLE;
        private double maxDistance = DEFAULT_MAX_DISTANCE;
        private double interactionReach = DEFAULT_INTERACTION_REACH;
        private boolean renderWhenScreenOpen;

        private Builder(String url) {
            this.url = url;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder surfaceId(Identifier surfaceId) {
            this.surfaceId = Objects.requireNonNull(surfaceId, "surfaceId");
            return this;
        }

        public Builder owner(Object owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        public Builder surfaceConfig(BrowserSurfaceConfig surfaceConfig) {
            this.surfaceConfig = Objects.requireNonNull(surfaceConfig, "surfaceConfig");
            return this;
        }

        public Builder surfaceSize(int width, int height) {
            this.surfaceWidth = requirePositive(width, "surfaceWidth");
            this.surfaceHeight = requirePositive(height, "surfaceHeight");
            return this;
        }

        public Builder resolution(int width, int height) {
            this.resolutionWidth = requirePositive(width, "resolutionWidth");
            this.resolutionHeight = requirePositive(height, "resolutionHeight");
            return this;
        }

        public Builder maxFps(int maxFps) {
            this.maxFps = requirePositive(maxFps, "maxFps");
            return this;
        }

        public Builder dimension(ResourceKey<Level> dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder position(Vec3 position) {
            this.position = Objects.requireNonNull(position, "position");
            return this;
        }

        public Builder worldSize(float width, float height) {
            this.worldWidth = requirePositive(width, "worldWidth");
            this.worldHeight = requirePositive(height, "worldHeight");
            return this;
        }

        public Builder rotation(Quaternionfc rotation) {
            this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
            return this;
        }

        public Builder orientation(GrapheneWorldSurfaceOrientation orientation) {
            this.rotation = Objects.requireNonNull(orientation, "orientation").rotation();
            return this;
        }

        public Builder rotationDegrees(float pitch, float yaw, float roll) {
            this.rotation = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(pitch),
                    (float) Math.toRadians(yaw),
                    (float) Math.toRadians(roll)
            );
            return this;
        }

        public Builder horizontalUp() {
            return orientation(GrapheneWorldSurfaceOrientation.horizontalUp());
        }

        public Builder horizontalDown() {
            return orientation(GrapheneWorldSurfaceOrientation.horizontalDown());
        }

        public Builder vertical(Direction front) {
            return orientation(GrapheneWorldSurfaceOrientation.vertical(front));
        }

        public Builder blockFace(Direction front) {
            return orientation(GrapheneWorldSurfaceOrientation.blockFace(front));
        }

        public Builder facing(GrapheneWorldSurfaceFacing facing) {
            this.facing = Objects.requireNonNull(facing, "facing");
            return this;
        }

        public Builder side(GrapheneWorldSurfaceSide side) {
            this.side = Objects.requireNonNull(side, "side");
            return this;
        }

        public Builder maxDistance(double maxDistance) {
            this.maxDistance = requirePositive(maxDistance, "maxDistance");
            return this;
        }

        public Builder interactionReach(double interactionReach) {
            this.interactionReach = requirePositive(interactionReach, "interactionReach");
            return this;
        }

        public Builder renderWhenScreenOpen(boolean renderWhenScreenOpen) {
            this.renderWhenScreenOpen = renderWhenScreenOpen;
            return this;
        }

        public GrapheneWorldSurfaceConfig build() {
            return new GrapheneWorldSurfaceConfig(this);
        }
    }
}
