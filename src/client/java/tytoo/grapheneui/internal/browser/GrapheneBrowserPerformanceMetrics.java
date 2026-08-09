package tytoo.grapheneui.internal.browser;

import tytoo.grapheneui.api.surface.BrowserSurfacePerformanceSnapshot;

import java.util.concurrent.atomic.LongAdder;

final class GrapheneBrowserPerformanceMetrics {
    private final LongAdder paintFrames = new LongAdder();
    private final LongAdder fullFrameCopies = new LongAdder();
    private final LongAdder partialFrameCopies = new LongAdder();
    private final LongAdder capturedBytes = new LongAdder();
    private final LongAdder uploadedFrames = new LongAdder();
    private final LongAdder fullFrameUploads = new LongAdder();
    private final LongAdder partialFrameUploads = new LongAdder();
    private final LongAdder uploadedBytes = new LongAdder();
    private final LongAdder coalescedPaintFrames = new LongAdder();
    private final LongAdder dirtyHistoryFallbacks = new LongAdder();
    private final LongAdder captureNanos = new LongAdder();
    private final LongAdder uploadNanos = new LongAdder();
    private final LongAdder externalBeginFrames = new LongAdder();

    void recordCapture(GrapheneDirtyRegion copiedDamage, int width, int height, long elapsedNanos) {
        paintFrames.increment();
        if (copiedDamage.isFull()) {
            fullFrameCopies.increment();
        } else {
            partialFrameCopies.increment();
        }
        capturedBytes.add(copiedDamage.pixelCount(width, height) * 4L);
        captureNanos.add(elapsedNanos);
        if (copiedDamage.isHistoryFallback()) {
            dirtyHistoryFallbacks.increment();
        }
    }

    void recordUpload(boolean full, long bytes, long coalescedFrames, boolean historyFallback, long elapsedNanos) {
        uploadedFrames.increment();
        if (full) {
            fullFrameUploads.increment();
        } else {
            partialFrameUploads.increment();
        }
        uploadedBytes.add(bytes);
        coalescedPaintFrames.add(coalescedFrames);
        uploadNanos.add(elapsedNanos);
        if (historyFallback) {
            dirtyHistoryFallbacks.increment();
        }
    }

    void recordExternalBeginFrame() {
        externalBeginFrames.increment();
    }

    BrowserSurfacePerformanceSnapshot snapshot() {
        return new BrowserSurfacePerformanceSnapshot(
                paintFrames.sum(),
                fullFrameCopies.sum(),
                partialFrameCopies.sum(),
                capturedBytes.sum(),
                uploadedFrames.sum(),
                fullFrameUploads.sum(),
                partialFrameUploads.sum(),
                uploadedBytes.sum(),
                coalescedPaintFrames.sum(),
                dirtyHistoryFallbacks.sum(),
                captureNanos.sum(),
                uploadNanos.sum(),
                externalBeginFrames.sum()
        );
    }
}
