package tytoo.grapheneui.api.surface;

/**
 * Controls how Chromium schedules off-screen browser frames.
 */
public enum BrowserSurfaceFrameScheduling {
    /** Chromium schedules paint frames up to the configured maximum frame rate. */
    AUTOMATIC,

    /**
     * Graphene requests a Chromium frame when the surface is rendered or prepared as a texture.
     * This avoids producing animation frames that Minecraft never consumes.
     */
    RENDER_DRIVEN
}
