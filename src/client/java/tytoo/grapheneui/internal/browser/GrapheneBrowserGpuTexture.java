package tytoo.grapheneui.internal.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.resources.Identifier;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

final class GrapheneBrowserGpuTexture implements AutoCloseable {
    private static final long NEVER_UPLOADED = Long.MIN_VALUE;
    private static final AtomicInteger NEXT_TEXTURE_SEQUENCE = new AtomicInteger();

    private final String label;
    private final Identifier textureId;
    private GpuTexture texture;
    private GpuTextureView view;
    private long lastUploadedVersion = NEVER_UPLOADED;
    private boolean registered;

    GrapheneBrowserGpuTexture(String label) {
        this.label = label;
        this.textureId = Identifier.fromNamespaceAndPath(
                GrapheneCore.ID,
                "browser/" + sanitizeLabel(label) + "_" + NEXT_TEXTURE_SEQUENCE.incrementAndGet()
        );
    }

    private static String sanitizeLabel(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    void ensureSize(int width, int height) {
        if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height) {
            return;
        }

        close();
        texture = RenderSystem.getDevice().createTexture(
                () -> label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
        view = RenderSystem.getDevice().createTextureView(texture);
        lastUploadedVersion = NEVER_UPLOADED;
    }

    void ensureRegistered() {
        if (registered) {
            return;
        }

        McClient.registerTexture(
                textureId,
                new GrapheneBrowserRegisteredTexture(
                        this,
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                )
        );
        registered = true;
    }

    Identifier textureId() {
        return textureId;
    }

    GpuTexture texture() {
        return texture;
    }

    GpuTextureView view() {
        return view;
    }

    boolean isUploaded(long frameVersion) {
        return lastUploadedVersion == frameVersion;
    }

    void markUploaded(long frameVersion) {
        lastUploadedVersion = frameVersion;
    }

    @Override
    public void close() {
        if (registered) {
            McClient.releaseTexture(textureId);
            registered = false;
        }

        if (view != null) {
            view.close();
            view = null;
        }

        if (texture != null) {
            texture.close();
            texture = null;
        }

        lastUploadedVersion = NEVER_UPLOADED;
    }
}
