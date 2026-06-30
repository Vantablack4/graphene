package tytoo.grapheneui.api.surface;

import net.minecraft.resources.Identifier;

import java.util.Objects;

@SuppressWarnings("unused")
public final class BrowserSurfaceTextureFrame {
    private final Identifier textureId;
    private final int textureWidth;
    private final int textureHeight;
    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;

    public BrowserSurfaceTextureFrame(
            Identifier textureId,
            int textureWidth,
            int textureHeight,
            float u0,
            float u1,
            float v0,
            float v1
    ) {
        this.textureId = Objects.requireNonNull(textureId, "textureId");
        this.textureWidth = requirePositive(textureWidth, "textureWidth");
        this.textureHeight = requirePositive(textureHeight, "textureHeight");
        this.u0 = requireFinite(u0, "u0");
        this.u1 = requireFinite(u1, "u1");
        this.v0 = requireFinite(v0, "v0");
        this.v1 = requireFinite(v1, "v1");
    }

    public Identifier textureId() {
        return textureId;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public float u0() {
        return u0;
    }

    public float u1() {
        return u1;
    }

    public float v0() {
        return v0;
    }

    public float v1() {
        return v1;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }

        return value;
    }

    private static float requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }

        return value;
    }
}
