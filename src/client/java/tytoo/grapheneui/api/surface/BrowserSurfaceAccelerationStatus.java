package tytoo.grapheneui.api.surface;

import java.util.Objects;

/**
 * Describes the active Chromium-to-Minecraft paint transport and shared-texture availability.
 */
public record BrowserSurfaceAccelerationStatus(
        boolean sharedTextureRequested,
        boolean sharedTextureActive,
        String activePath,
        String sharedTextureUnavailableReason
) {
    public static final String SOFTWARE_OSR_BUFFER_PATH = "SOFTWARE_OSR_BUFFER";
    public static final String SHARED_TEXTURE_UNAVAILABLE_REASON =
            "CEF exposes platform-native shared handles only for the duration of its UI-thread paint callback, "
                    + "while Minecraft texture import must run on the render thread; Graphene cannot safely retain, "
                    + "import, or synchronize that handle with the current JCEF/Minecraft contract.";

    public BrowserSurfaceAccelerationStatus {
        Objects.requireNonNull(activePath, "activePath");
        Objects.requireNonNull(sharedTextureUnavailableReason, "sharedTextureUnavailableReason");
    }

    static BrowserSurfaceAccelerationStatus softwareFallback(boolean requested) {
        return new BrowserSurfaceAccelerationStatus(
                requested,
                false,
                SOFTWARE_OSR_BUFFER_PATH,
                SHARED_TEXTURE_UNAVAILABLE_REASON
        );
    }
}
