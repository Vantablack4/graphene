package tytoo.grapheneui.api.world;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldAnchor {
    public static final double DEFAULT_MAX_DISTANCE = -1.0D;
    public static final int DEFAULT_PRIORITY = 0;

    private final String id;
    private final String kind;
    private final ResourceKey<Level> dimension;
    private final Vec3 position;
    private final AABB bounds;
    private final double maxDistance;
    private final int priority;
    private final int screenOffsetX;
    private final int screenOffsetY;
    private final boolean interactive;
    private final GrapheneWorldAnchorOcclusion occlusion;
    private final JsonElement payload;

    private GrapheneWorldAnchor(Builder builder) {
        this.id = requireNonBlank(builder.id, "id");
        this.kind = requireNonBlank(builder.kind, "kind");
        this.dimension = builder.dimension;
        this.position = Objects.requireNonNull(builder.position, "position");
        this.bounds = builder.bounds;
        this.maxDistance = builder.maxDistance;
        this.priority = builder.priority;
        this.screenOffsetX = builder.screenOffsetX;
        this.screenOffsetY = builder.screenOffsetY;
        this.interactive = builder.interactive;
        this.occlusion = Objects.requireNonNull(builder.occlusion, "occlusion");
        this.payload = builder.payload;
    }

    public static Builder builder(String id, Vec3 position) {
        return new Builder(id, position);
    }

    public GrapheneWorldAnchor withPosition(Vec3 position) {
        return copyBuilder()
                .position(position)
                .build();
    }

    public GrapheneWorldAnchor withPayload(JsonElement payload) {
        return copyBuilder()
                .payload(payload)
                .build();
    }

    public Builder copyBuilder() {
        return builder(id, position)
                .kind(kind)
                .dimension(dimension)
                .bounds(bounds)
                .maxDistance(maxDistance)
                .priority(priority)
                .screenOffset(screenOffsetX, screenOffsetY)
                .interactive(interactive)
                .occlusion(occlusion)
                .payload(payload);
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Vec3 position() {
        return position;
    }

    public AABB bounds() {
        return bounds;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public int priority() {
        return priority;
    }

    public int screenOffsetX() {
        return screenOffsetX;
    }

    public int screenOffsetY() {
        return screenOffsetY;
    }

    public boolean interactive() {
        return interactive;
    }

    public GrapheneWorldAnchorOcclusion occlusion() {
        return occlusion;
    }

    public JsonElement payload() {
        return payload;
    }

    private static String requireNonBlank(String value, String name) {
        String normalizedValue = Objects.requireNonNull(value, name).trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return normalizedValue;
    }

    public static final class Builder {
        private String id;
        private String kind = "default";
        private ResourceKey<Level> dimension;
        private Vec3 position;
        private AABB bounds;
        private double maxDistance = DEFAULT_MAX_DISTANCE;
        private int priority = DEFAULT_PRIORITY;
        private int screenOffsetX;
        private int screenOffsetY;
        private boolean interactive;
        private GrapheneWorldAnchorOcclusion occlusion = GrapheneWorldAnchorOcclusion.NONE;
        private JsonElement payload;

        private Builder(String id, Vec3 position) {
            this.id = id;
            this.position = position;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder dimension(ResourceKey<Level> dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder position(Vec3 position) {
            this.position = position;
            return this;
        }

        public Builder bounds(AABB bounds) {
            this.bounds = bounds;
            return this;
        }

        public Builder maxDistance(double maxDistance) {
            if (maxDistance != DEFAULT_MAX_DISTANCE && (!Double.isFinite(maxDistance) || maxDistance <= 0.0D)) {
                throw new IllegalArgumentException("maxDistance must be finite and > 0, or -1 to use the layer default");
            }

            this.maxDistance = maxDistance;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder screenOffset(int x, int y) {
            this.screenOffsetX = x;
            this.screenOffsetY = y;
            return this;
        }

        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        public Builder occlusion(GrapheneWorldAnchorOcclusion occlusion) {
            this.occlusion = Objects.requireNonNull(occlusion, "occlusion");
            return this;
        }

        public Builder payload(JsonElement payload) {
            this.payload = payload;
            return this;
        }

        public GrapheneWorldAnchor build() {
            return new GrapheneWorldAnchor(this);
        }
    }
}
