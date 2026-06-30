package tytoo.grapheneui.internal.browser;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.Objects;
import java.util.function.Supplier;

final class GrapheneBrowserRegisteredTexture extends AbstractTexture {
    private final GrapheneBrowserGpuTexture source;
    private final Supplier<GpuSampler> samplerSupplier;

    GrapheneBrowserRegisteredTexture(GrapheneBrowserGpuTexture source, Supplier<GpuSampler> samplerSupplier) {
        this.source = Objects.requireNonNull(source, "source");
        this.samplerSupplier = Objects.requireNonNull(samplerSupplier, "samplerSupplier");
    }

    @Override
    public GpuTexture getTexture() {
        return source.texture();
    }

    @Override
    public GpuTextureView getTextureView() {
        return source.view();
    }

    @Override
    public GpuSampler getSampler() {
        return samplerSupplier.get();
    }

    @Override
    public void close() {
        // The owning GrapheneBrowserGpuTexture controls the real GPU texture lifetime.
    }
}
