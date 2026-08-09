package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.surface.BrowserSurfacePerformanceSnapshot;

import java.awt.*;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class GraphenePaintBufferTest {
    @Test
    void snapshotCombinesDamageAcrossPaintsSkippedByTheTexture() {
        GraphenePaintBuffer paintBuffer = new GraphenePaintBuffer();
        paintBuffer.capture(false, null, frame(8, 8, (byte) 1), 8, 8);
        long uploadedVersion = paintBuffer.snapshot().mainFrame().frameVersion();

        paintBuffer.capture(
                false,
                new Rectangle[]{new Rectangle(1, 1, 2, 2)},
                frame(8, 8, (byte) 2),
                8,
                8
        );
        paintBuffer.capture(
                false,
                new Rectangle[]{new Rectangle(2, 2, 2, 2)},
                frame(8, 8, (byte) 3),
                8,
                8
        );

        GraphenePaintBuffer.FrameView frame = paintBuffer.snapshot(uploadedVersion, -1L).mainFrame();

        assertFalse(frame.fullReRender());
        assertEquals(7L, pixelCount(frame.dirtyRects()));
    }

    @Test
    void warmedSlotsCopyOnlyAccumulatedDamageAndKeepACompleteFrame() {
        int width = 4;
        int height = 4;
        GrapheneBrowserPerformanceMetrics metrics = new GrapheneBrowserPerformanceMetrics();
        GraphenePaintBuffer paintBuffer = new GraphenePaintBuffer(metrics);
        paintBuffer.capture(false, null, frame(width, height, (byte) 10), width, height);

        for (int changedPixels = 1; changedPixels <= 4; changedPixels++) {
            ByteBuffer source = frame(width, height, (byte) 10);
            for (int x = 0; x < changedPixels; x++) {
                setPixel(source, width, x, 1, (byte) (11 + x));
            }
            paintBuffer.capture(
                    false,
                    new Rectangle[]{new Rectangle(changedPixels - 1, 1, 1, 1)},
                    source,
                    width,
                    height
            );
        }

        GraphenePaintBuffer.FrameView frame = paintBuffer.snapshot().mainFrame();
        for (int x = 0; x < 4; x++) {
            int changedPixelIndex = ((width + x) * 4);
            assertEquals((byte) (11 + x), frame.buffer().get(changedPixelIndex));
        }
        assertEquals((byte) 10, frame.buffer().get(0));

        BrowserSurfacePerformanceSnapshot snapshot = metrics.snapshot();
        assertEquals(4L, snapshot.fullFrameCopies());
        assertEquals(1L, snapshot.partialFrameCopies());
        assertEquals((4L * width * height + 4L) * 4L, snapshot.capturedBytes());
    }

    private static ByteBuffer frame(int width, int height, byte value) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
        while (buffer.hasRemaining()) {
            buffer.put(value);
        }
        buffer.position(0);
        return buffer;
    }

    private static void setPixel(ByteBuffer buffer, int width, int x, int y, byte value) {
        int start = ((y * width + x) * 4);
        for (int component = 0; component < 4; component++) {
            buffer.put(start + component, value);
        }
    }

    private static long pixelCount(Rectangle[] rectangles) {
        long pixels = 0L;
        for (Rectangle rectangle : rectangles) {
            pixels += (long) rectangle.width * rectangle.height;
        }
        return pixels;
    }
}
