package tytoo.grapheneui.internal.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneMimeTypesTest {
    @Test
    void resolvesKnownMimeTypeFromPath() {
        String mimeType = GrapheneMimeTypes.resolve("assets/my-mod-id/web/app.js");

        assertEquals("application/javascript", mimeType);
    }

    @Test
    void resolvesBinaryGltfMimeTypeCaseInsensitively() {
        String mimeType = GrapheneMimeTypes.resolve("assets/my-mod-id/models/lock.GLB");

        assertEquals("model/gltf-binary", mimeType);
    }

    @Test
    void resolvesJsonGltfMimeType() {
        String mimeType = GrapheneMimeTypes.resolve("assets/my-mod-id/models/lock.gltf");

        assertEquals("model/gltf+json", mimeType);
    }

    @Test
    void resolvesBrowserVideoMimeTypes() {
        assertEquals("video/webm", GrapheneMimeTypes.resolve("assets/my-mod-id/video/menu.WEBM"));
        assertEquals("video/mp4", GrapheneMimeTypes.resolve("assets/my-mod-id/video/menu.mp4"));
    }

    @Test
    void fallsBackToTextPlainForUnknownExtension() {
        String mimeType = GrapheneMimeTypes.resolve("assets/my-mod-id/web/custom.unknown");

        assertEquals(GrapheneMimeTypes.DEFAULT_MIME_TYPE, mimeType);
    }
}
