package tytoo.grapheneui.api.surface;

/**
 * Immutable cumulative performance counters for a browser surface.
 *
 * <p>Timing values measure Graphene's CPU work and texture submission time. They do not wait for
 * the GPU to finish the submitted upload.</p>
 */
public record BrowserSurfacePerformanceSnapshot(
        long paintFrames,
        long fullFrameCopies,
        long partialFrameCopies,
        long capturedBytes,
        long uploadedFrames,
        long fullFrameUploads,
        long partialFrameUploads,
        long uploadedBytes,
        long coalescedPaintFrames,
        long dirtyHistoryFallbacks,
        long captureNanos,
        long uploadNanos,
        long externalBeginFrames
) {
    public double captureMilliseconds() {
        return captureNanos / 1_000_000.0D;
    }

    public double uploadMilliseconds() {
        return uploadNanos / 1_000_000.0D;
    }
}
