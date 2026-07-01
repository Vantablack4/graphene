package tytoo.grapheneui.internal.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tytoo.grapheneui.api.surface.BrowserSurfaceTextureFrame;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

final class GrapheneBrowserGpuRenderer implements AutoCloseable {
    private final GrapheneBrowserGpuTexture mainTexture = new GrapheneBrowserGpuTexture("Graphene Browser Main");
    private final GrapheneBrowserGpuTexture popupTexture = new GrapheneBrowserGpuTexture("Graphene Browser Popup");
    private final GrapheneBrowserFrameUploader frameUploader;
    private BrowserSurfaceTextureFrame cachedMainFrameTextureFrame;
    private long cachedMainFrameVersion = Long.MIN_VALUE;
    private int cachedMainFrameWidth;
    private int cachedMainFrameHeight;
    private int cachedMainFrameRegionX;
    private int cachedMainFrameRegionY;
    private int cachedMainFrameRegionWidth;
    private int cachedMainFrameRegionHeight;

    GrapheneBrowserGpuRenderer(boolean transparent) {
        this.frameUploader = new GrapheneBrowserFrameUploader(transparent);
    }

    void render(
            GuiGraphicsExtractor graphics,
            GraphenePaintBuffer.Snapshot snapshot,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight
    ) {
        GraphenePaintBuffer.FrameView mainFrame = snapshot.mainFrame();
        if (mainFrame == null || width <= 0 || height <= 0) {
            return;
        }

        GrapheneBrowserRenderBounds.Region visibleRegion = GrapheneBrowserRenderBounds.clampRegion(
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
                mainFrame.width(),
                mainFrame.height()
        );
        if (visibleRegion == null) {
            return;
        }

        mainTexture.ensureSize(mainFrame.width(), mainFrame.height());
        frameUploader.uploadIfNeeded(mainTexture, mainFrame);
        submitBlit(
                graphics,
                mainTexture,
                x,
                y,
                width,
                height,
                visibleRegion.x(),
                visibleRegion.y(),
                visibleRegion.width(),
                visibleRegion.height(),
                mainFrame.width(),
                mainFrame.height()
        );

        GraphenePaintBuffer.PopupFrameView popupFrame = snapshot.popupFrame();
        if (popupFrame == null) {
            return;
        }

        GrapheneBrowserRenderBounds.PopupPlacement popupPlacement = GrapheneBrowserRenderBounds.placePopup(
                popupFrame.popupRect(),
                visibleRegion,
                x,
                y,
                width,
                height,
                popupFrame.width(),
                popupFrame.height()
        );
        if (popupPlacement == null) {
            return;
        }

        popupTexture.ensureSize(popupFrame.width(), popupFrame.height());
        frameUploader.uploadIfNeeded(popupTexture, popupFrame);
        submitBlit(
                graphics,
                popupTexture,
                popupPlacement.x(),
                popupPlacement.y(),
                popupPlacement.width(),
                popupPlacement.height(),
                popupPlacement.sourceX(),
                popupPlacement.sourceY(),
                popupPlacement.sourceWidth(),
                popupPlacement.sourceHeight(),
                popupFrame.width(),
                popupFrame.height()
        );
    }

    BrowserSurfaceTextureFrame prepareMainFrameTexture(
            GraphenePaintBuffer.Snapshot snapshot,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight
    ) {
        GraphenePaintBuffer.FrameView mainFrame = snapshot.mainFrame();
        if (mainFrame == null) {
            return null;
        }

        GrapheneBrowserRenderBounds.Region visibleRegion = GrapheneBrowserRenderBounds.clampRegion(
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
                mainFrame.width(),
                mainFrame.height()
        );
        if (visibleRegion == null) {
            return null;
        }

        BrowserSurfaceTextureFrame cachedFrame = cachedMainFrameTextureFrame;
        if (cachedFrame != null
                && cachedMainFrameVersion == mainFrame.frameVersion()
                && cachedMainFrameWidth == mainFrame.width()
                && cachedMainFrameHeight == mainFrame.height()
                && cachedMainFrameRegionX == visibleRegion.x()
                && cachedMainFrameRegionY == visibleRegion.y()
                && cachedMainFrameRegionWidth == visibleRegion.width()
                && cachedMainFrameRegionHeight == visibleRegion.height()
                && mainTexture.isUploaded(mainFrame.frameVersion())) {
            return cachedFrame;
        }

        mainTexture.ensureSize(mainFrame.width(), mainFrame.height());
        frameUploader.uploadIfNeeded(mainTexture, mainFrame);
        mainTexture.ensureRegistered();

        BrowserSurfaceTextureFrame textureFrame = new BrowserSurfaceTextureFrame(
                mainTexture.textureId(),
                mainFrame.width(),
                mainFrame.height(),
                (float) visibleRegion.x() / mainFrame.width(),
                (float) (visibleRegion.x() + visibleRegion.width()) / mainFrame.width(),
                (float) visibleRegion.y() / mainFrame.height(),
                (float) (visibleRegion.y() + visibleRegion.height()) / mainFrame.height()
        );
        cachedMainFrameTextureFrame = textureFrame;
        cachedMainFrameVersion = mainFrame.frameVersion();
        cachedMainFrameWidth = mainFrame.width();
        cachedMainFrameHeight = mainFrame.height();
        cachedMainFrameRegionX = visibleRegion.x();
        cachedMainFrameRegionY = visibleRegion.y();
        cachedMainFrameRegionWidth = visibleRegion.width();
        cachedMainFrameRegionHeight = visibleRegion.height();
        return textureFrame;
    }

    CompletableFuture<BufferedImage> createScreenshot(GraphenePaintBuffer.Snapshot snapshot) {
        return frameUploader.createScreenshot(snapshot);
    }

    @Override
    public void close() {
        cachedMainFrameTextureFrame = null;
        cachedMainFrameVersion = Long.MIN_VALUE;
        cachedMainFrameWidth = 0;
        cachedMainFrameHeight = 0;
        mainTexture.close();
        popupTexture.close();
        frameUploader.close();
    }

    private void submitBlit(
            GuiGraphicsExtractor graphics,
            GrapheneBrowserGpuTexture texture,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight
    ) {
        float u0 = (float) sourceX / textureWidth;
        float u1 = (float) (sourceX + sourceWidth) / textureWidth;
        float v0 = (float) sourceY / textureHeight;
        float v1 = (float) (sourceY + sourceHeight) / textureHeight;

        graphics.blit(
                texture.view(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                x,
                y,
                x + width,
                y + height,
                u0,
                u1,
                v0,
                v1
        );
    }
}
