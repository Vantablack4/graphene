package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBrowserFrameUploaderTest {
    @Test
    void transparentPixelSwizzlePreservesAlphaAndReordersBgraToRgba() {
        assertEquals(0x56341278, GrapheneBrowserFrameUploader.swizzlePixel(0x12345678, true));
    }

    @Test
    void opaquePixelSwizzleForcesFullAlpha() {
        assertEquals(0x563412FF, GrapheneBrowserFrameUploader.swizzlePixel(0x12345678, false));
    }

    @Test
    void fullUploadThresholdCountsOverlappingDamageOnlyOnce() {
        Rectangle[] overlappingDamage = {
                new Rectangle(0, 0, 40, 100),
                new Rectangle(0, 0, 40, 100)
        };

        assertFalse(GrapheneBrowserFrameUploader.shouldUploadFullFrame(overlappingDamage, 100, 100));
        assertTrue(GrapheneBrowserFrameUploader.shouldUploadFullFrame(
                new Rectangle[]{new Rectangle(0, 0, 46, 100)},
                100,
                100
        ));
    }

    @Test
    void aRecreatedTextureAlwaysReceivesAFullUploadAfterResize() {
        Rectangle[] smallDamage = {new Rectangle(1, 1, 2, 2)};

        assertTrue(GrapheneBrowserFrameUploader.requiresFullUpload(
                Long.MIN_VALUE,
                false,
                smallDamage,
                100,
                100
        ));
        assertFalse(GrapheneBrowserFrameUploader.requiresFullUpload(
                7L,
                false,
                smallDamage,
                100,
                100
        ));
    }
}
