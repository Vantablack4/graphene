package tytoo.grapheneui.api.world;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import tytoo.grapheneui.api.surface.BrowserSurfaceConfig;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldOverlayConfig {
    public static final int DEFAULT_MAX_ANCHORS = 64;
    public static final int DEFAULT_MAX_FPS = 30;
    public static final double DEFAULT_MAX_DISTANCE = 32.0D;
    public static final int DEFAULT_SCREEN_MARGIN = 64;
    public static final int DEFAULT_OCCLUSION_INTERVAL_TICKS = 5;

    private final String url;
    private final int maxAnchors;
    private final int maxFps;
    private final double defaultMaxDistance;
    private final int screenMargin;
    private final int occlusionIntervalTicks;
    private final Identifier elementId;
    private final Identifier renderAfter;
    private final BrowserSurfaceConfig surfaceConfig;
    private final Object owner;
    private final boolean renderWhenScreenOpen;

    private GrapheneWorldOverlayConfig(Builder builder) {
        this.url = requireNonBlank(builder.url, "url");
        this.maxAnchors = builder.maxAnchors;
        this.maxFps = builder.maxFps;
        this.defaultMaxDistance = builder.defaultMaxDistance;
        this.screenMargin = builder.screenMargin;
        this.occlusionIntervalTicks = builder.occlusionIntervalTicks;
        this.elementId = builder.elementId;
        this.renderAfter = Objects.requireNonNull(builder.renderAfter, "renderAfter");
        this.surfaceConfig = Objects.requireNonNull(builder.surfaceConfig, "surfaceConfig").withMaxFps(this.maxFps);
        this.owner = builder.owner;
        this.renderWhenScreenOpen = builder.renderWhenScreenOpen;
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    public String url() {
        return url;
    }

    public int maxAnchors() {
        return maxAnchors;
    }

    public int maxFps() {
        return maxFps;
    }

    public double defaultMaxDistance() {
        return defaultMaxDistance;
    }

    public int screenMargin() {
        return screenMargin;
    }

    public int occlusionIntervalTicks() {
        return occlusionIntervalTicks;
    }

    public Identifier elementId() {
        return elementId;
    }

    public Identifier renderAfter() {
        return renderAfter;
    }

    public BrowserSurfaceConfig surfaceConfig() {
        return surfaceConfig;
    }

    public Object owner() {
        return owner;
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

    private static double requirePositive(double value, String name) {
        if (value <= 0.0D || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }

        return value;
    }

    public static final class Builder {
        private String url;
        private int maxAnchors = DEFAULT_MAX_ANCHORS;
        private int maxFps = DEFAULT_MAX_FPS;
        private double defaultMaxDistance = DEFAULT_MAX_DISTANCE;
        private int screenMargin = DEFAULT_SCREEN_MARGIN;
        private int occlusionIntervalTicks = DEFAULT_OCCLUSION_INTERVAL_TICKS;
        private Identifier elementId;
        private Identifier renderAfter = VanillaHudElements.CROSSHAIR;
        private BrowserSurfaceConfig surfaceConfig = BrowserSurfaceConfig.defaults();
        private Object owner;
        private boolean renderWhenScreenOpen;

        private Builder(String url) {
            this.url = url;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder maxAnchors(int maxAnchors) {
            this.maxAnchors = requirePositive(maxAnchors, "maxAnchors");
            return this;
        }

        public Builder maxFps(int maxFps) {
            this.maxFps = requirePositive(maxFps, "maxFps");
            return this;
        }

        public Builder defaultMaxDistance(double defaultMaxDistance) {
            this.defaultMaxDistance = requirePositive(defaultMaxDistance, "defaultMaxDistance");
            return this;
        }

        public Builder screenMargin(int screenMargin) {
            if (screenMargin < 0) {
                throw new IllegalArgumentException("screenMargin must be >= 0");
            }

            this.screenMargin = screenMargin;
            return this;
        }

        public Builder occlusionIntervalTicks(int occlusionIntervalTicks) {
            this.occlusionIntervalTicks = requirePositive(occlusionIntervalTicks, "occlusionIntervalTicks");
            return this;
        }

        public Builder elementId(Identifier elementId) {
            this.elementId = Objects.requireNonNull(elementId, "elementId");
            return this;
        }

        public Builder renderAfter(Identifier renderAfter) {
            this.renderAfter = Objects.requireNonNull(renderAfter, "renderAfter");
            return this;
        }

        public Builder surfaceConfig(BrowserSurfaceConfig surfaceConfig) {
            this.surfaceConfig = Objects.requireNonNull(surfaceConfig, "surfaceConfig");
            return this;
        }

        public Builder owner(Object owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        public Builder renderWhenScreenOpen(boolean renderWhenScreenOpen) {
            this.renderWhenScreenOpen = renderWhenScreenOpen;
            return this;
        }

        public GrapheneWorldOverlayConfig build() {
            return new GrapheneWorldOverlayConfig(this);
        }
    }
}
