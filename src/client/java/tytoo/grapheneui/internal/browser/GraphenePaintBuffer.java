package tytoo.grapheneui.internal.browser;

import java.awt.*;
import java.nio.ByteBuffer;

final class GraphenePaintBuffer {
    private static final int BYTES_PER_PIXEL = 4;
    private static final int SLOT_COUNT = 4;
    private static final long NO_BASE_VERSION = -1L;

    private final MainFrameData mainFrameData = new MainFrameData();
    private final PopupFrameData popupFrameData = new PopupFrameData();
    private final Object mainCaptureLock = new Object();
    private final Object popupCaptureLock = new Object();
    private final GrapheneBrowserPerformanceMetrics performanceMetrics;

    GraphenePaintBuffer() {
        this(null);
    }

    GraphenePaintBuffer(GrapheneBrowserPerformanceMetrics performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    void capture(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (popup) {
            capturePopup(buffer, width, height);
            return;
        }

        captureMain(dirtyRects, buffer, width, height);
    }

    void onPopupSize(Rectangle rect) {
        synchronized (popupFrameData) {
            if (rect.width <= 0 || rect.height <= 0) {
                popupFrameData.popupVisible = false;
                popupFrameData.popupRect.setBounds(0, 0, 0, 0);
                return;
            }

            popupFrameData.popupRect.setBounds(rect);
            popupFrameData.popupVisible = true;
        }
    }

    void onPopupClosed() {
        synchronized (popupFrameData) {
            popupFrameData.popupVisible = false;
            popupFrameData.popupRect.setBounds(0, 0, 0, 0);
        }
    }

    Snapshot snapshot() {
        return snapshot(NO_BASE_VERSION, NO_BASE_VERSION);
    }

    Snapshot snapshot(long mainBaseVersion, long popupBaseVersion) {
        synchronized (mainFrameData) {
            synchronized (popupFrameData) {
                return new Snapshot(
                        createMainFrameView(mainBaseVersion),
                        createPopupFrameView(popupBaseVersion)
                );
            }
        }
    }

    private void captureMain(Rectangle[] dirtyRects, ByteBuffer sourceBuffer, int width, int height) {
        long startedAt = performanceMetrics == null ? 0L : System.nanoTime();
        GrapheneDirtyRegion currentDamage = GrapheneDirtyRegion.from(dirtyRects, width, height);
        GrapheneDirtyRegion copiedDamage;

        synchronized (mainCaptureLock) {
            MainCapturePlan plan;
            synchronized (mainFrameData) {
                int writeIndex = mainFrameData.selectWriteSlot();
                FrameSlot existingSlot = mainFrameData.slots[writeIndex];
                boolean reusable = existingSlot != null
                        && existingSlot.buffer != null
                        && existingSlot.buffer.capacity() == requiredBytes(width, height)
                        && existingSlot.width == width
                        && existingSlot.height == height;

                GrapheneDirtyRegion damageToCopy = GrapheneDirtyRegion.full();
                ByteBuffer targetBuffer = null;
                if (reusable) {
                    GrapheneDirtyRegion priorDamage = mainFrameData.dirtyHistory.damageSince(
                            existingSlot.frameVersion,
                            mainFrameData.frameVersion,
                            width,
                            height
                    );
                    damageToCopy = priorDamage.merge(currentDamage, width, height);
                    targetBuffer = existingSlot.buffer;
                }

                plan = new MainCapturePlan(
                        writeIndex,
                        mainFrameData.frameVersion + 1L,
                        targetBuffer,
                        damageToCopy
                );
            }

            ByteBuffer targetBuffer = plan.targetBuffer != null
                    ? plan.targetBuffer
                    : ByteBuffer.allocateDirect(requiredBytes(width, height));
            copyDamage(sourceBuffer, targetBuffer, width, height, plan.damageToCopy);

            synchronized (mainFrameData) {
                FrameSlot slot = mainFrameData.slot(plan.writeIndex);
                slot.buffer = targetBuffer;
                slot.width = width;
                slot.height = height;
                slot.frameVersion = plan.frameVersion;

                mainFrameData.frameVersion = plan.frameVersion;
                mainFrameData.dirtyHistory.add(plan.frameVersion, width, height, currentDamage);
                mainFrameData.latestSlotIndex = plan.writeIndex;
                mainFrameData.nextSlotIndex = (plan.writeIndex + 1) % SLOT_COUNT;
            }
            copiedDamage = plan.damageToCopy;
        }

        if (performanceMetrics != null) {
            performanceMetrics.recordCapture(copiedDamage, width, height, System.nanoTime() - startedAt);
        }
    }

    private void capturePopup(ByteBuffer sourceBuffer, int width, int height) {
        long startedAt = performanceMetrics == null ? 0L : System.nanoTime();
        GrapheneDirtyRegion copiedDamage = GrapheneDirtyRegion.full();

        synchronized (popupCaptureLock) {
            int writeIndex;
            long frameVersion;
            ByteBuffer targetBuffer;
            synchronized (popupFrameData) {
                writeIndex = popupFrameData.selectWriteSlot();
                FrameSlot existingSlot = popupFrameData.slots[writeIndex];
                targetBuffer = existingSlot != null
                        && existingSlot.buffer != null
                        && existingSlot.buffer.capacity() == requiredBytes(width, height)
                        ? existingSlot.buffer
                        : null;
                frameVersion = popupFrameData.frameVersion + 1L;
            }

            if (targetBuffer == null) {
                targetBuffer = ByteBuffer.allocateDirect(requiredBytes(width, height));
            }
            copyDamage(sourceBuffer, targetBuffer, width, height, copiedDamage);

            synchronized (popupFrameData) {
                FrameSlot slot = popupFrameData.slot(writeIndex);
                slot.buffer = targetBuffer;
                slot.width = width;
                slot.height = height;
                slot.frameVersion = frameVersion;

                popupFrameData.frameVersion = frameVersion;
                popupFrameData.latestSlotIndex = writeIndex;
                popupFrameData.nextSlotIndex = (writeIndex + 1) % SLOT_COUNT;
            }
        }

        if (performanceMetrics != null) {
            performanceMetrics.recordCapture(copiedDamage, width, height, System.nanoTime() - startedAt);
        }
    }

    private FrameView createMainFrameView(long baseVersion) {
        FrameSlot latestSlot = mainFrameData.latestSlot();
        if (latestSlot == null) {
            return null;
        }

        GrapheneDirtyRegion damage = baseVersion < 0L
                ? GrapheneDirtyRegion.full()
                : mainFrameData.dirtyHistory.damageSince(
                        baseVersion,
                        latestSlot.frameVersion,
                        latestSlot.width,
                        latestSlot.height
                );
        mainFrameData.protectedSlot = mainFrameData.latestSlotIndex;
        return new FrameView(
                latestSlot.buffer,
                latestSlot.width,
                latestSlot.height,
                damage.copyRectangles(),
                damage.isFull(),
                damage.isHistoryFallback(),
                latestSlot.frameVersion
        );
    }

    private PopupFrameView createPopupFrameView(long baseVersion) {
        if (!popupFrameData.popupVisible) {
            return null;
        }

        FrameSlot latestSlot = popupFrameData.latestSlot();
        if (latestSlot == null) {
            return null;
        }

        boolean alreadyUploaded = baseVersion == latestSlot.frameVersion;
        popupFrameData.protectedSlot = popupFrameData.latestSlotIndex;
        return new PopupFrameView(
                latestSlot.buffer,
                latestSlot.width,
                latestSlot.height,
                alreadyUploaded ? new Rectangle[0] : null,
                !alreadyUploaded,
                false,
                latestSlot.frameVersion,
                new Rectangle(popupFrameData.popupRect)
        );
    }

    private static int requiredBytes(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Browser frame dimensions must be > 0");
        }

        return Math.multiplyExact(Math.multiplyExact(width, height), BYTES_PER_PIXEL);
    }

    private static void copyDamage(
            ByteBuffer sourceBuffer,
            ByteBuffer targetBuffer,
            int width,
            int height,
            GrapheneDirtyRegion damage
    ) {
        if (damage.isFull()) {
            int size = requiredBytes(width, height);
            ByteBuffer source = sourceBuffer.duplicate();
            source.position(0);
            source.limit(size);
            ByteBuffer target = targetBuffer.duplicate();
            target.position(0);
            target.limit(size);
            target.put(source);
            return;
        }

        Rectangle[] rectangles = damage.copyRectangles();
        ByteBuffer source = sourceBuffer.duplicate();
        ByteBuffer target = targetBuffer.duplicate();
        for (Rectangle rectangle : rectangles) {
            int rowBytes = rectangle.width * BYTES_PER_PIXEL;
            for (int row = 0; row < rectangle.height; row++) {
                int start = ((rectangle.y + row) * width + rectangle.x) * BYTES_PER_PIXEL;
                source.limit(sourceBuffer.capacity());
                source.position(start);
                source.limit(start + rowBytes);
                target.limit(targetBuffer.capacity());
                target.position(start);
                target.put(source);
            }
        }
    }

    interface UploadView {
        ByteBuffer buffer();

        int width();

        int height();

        Rectangle[] dirtyRects();

        boolean fullReRender();

        boolean historyFallback();

        long frameVersion();
    }

    static final class Snapshot {
        private final FrameView mainFrame;
        private final PopupFrameView popupFrame;

        private Snapshot(FrameView mainFrame, PopupFrameView popupFrame) {
            this.mainFrame = mainFrame;
            this.popupFrame = popupFrame;
        }

        public FrameView mainFrame() {
            return mainFrame;
        }

        public PopupFrameView popupFrame() {
            return popupFrame;
        }
    }

    static final class FrameView implements UploadView {
        private final ByteBuffer buffer;
        private final int width;
        private final int height;
        private final Rectangle[] dirtyRects;
        private final boolean fullReRender;
        private final boolean historyFallback;
        private final long frameVersion;

        private FrameView(
                ByteBuffer buffer,
                int width,
                int height,
                Rectangle[] dirtyRects,
                boolean fullReRender,
                boolean historyFallback,
                long frameVersion
        ) {
            this.buffer = buffer;
            this.width = width;
            this.height = height;
            this.dirtyRects = dirtyRects;
            this.fullReRender = fullReRender;
            this.historyFallback = historyFallback;
            this.frameVersion = frameVersion;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public Rectangle[] dirtyRects() {
            return dirtyRects;
        }

        @Override
        public boolean fullReRender() {
            return fullReRender;
        }

        @Override
        public boolean historyFallback() {
            return historyFallback;
        }

        @Override
        public long frameVersion() {
            return frameVersion;
        }
    }

    static final class PopupFrameView implements UploadView {
        private final ByteBuffer buffer;
        private final int width;
        private final int height;
        private final Rectangle[] dirtyRects;
        private final boolean fullReRender;
        private final boolean historyFallback;
        private final long frameVersion;
        private final Rectangle popupRect;

        private PopupFrameView(
                ByteBuffer buffer,
                int width,
                int height,
                Rectangle[] dirtyRects,
                boolean fullReRender,
                boolean historyFallback,
                long frameVersion,
                Rectangle popupRect
        ) {
            this.buffer = buffer;
            this.width = width;
            this.height = height;
            this.dirtyRects = dirtyRects;
            this.fullReRender = fullReRender;
            this.historyFallback = historyFallback;
            this.frameVersion = frameVersion;
            this.popupRect = popupRect;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public Rectangle[] dirtyRects() {
            return dirtyRects;
        }

        @Override
        public boolean fullReRender() {
            return fullReRender;
        }

        @Override
        public boolean historyFallback() {
            return historyFallback;
        }

        @Override
        public long frameVersion() {
            return frameVersion;
        }

        public Rectangle popupRect() {
            return popupRect;
        }
    }

    private static class FrameData {
        final FrameSlot[] slots = new FrameSlot[SLOT_COUNT];
        int nextSlotIndex;
        int latestSlotIndex = -1;
        int protectedSlot = -1;
        long frameVersion;

        int selectWriteSlot() {
            for (int offset = 0; offset < SLOT_COUNT; offset++) {
                int index = (nextSlotIndex + offset) % SLOT_COUNT;
                if (index != protectedSlot && index != latestSlotIndex) {
                    return index;
                }
            }

            for (int offset = 0; offset < SLOT_COUNT; offset++) {
                int index = (nextSlotIndex + offset) % SLOT_COUNT;
                if (index != protectedSlot) {
                    return index;
                }
            }

            throw new IllegalStateException("No browser paint slot is available");
        }

        FrameSlot slot(int slotIndex) {
            FrameSlot slot = slots[slotIndex];
            if (slot == null) {
                slot = new FrameSlot();
                slots[slotIndex] = slot;
            }
            return slot;
        }

        FrameSlot latestSlot() {
            if (latestSlotIndex < 0) {
                return null;
            }

            return slots[latestSlotIndex];
        }
    }

    private static final class MainFrameData extends FrameData {
        private final GrapheneDirtyRectHistory dirtyHistory = new GrapheneDirtyRectHistory();
    }

    private static final class PopupFrameData extends FrameData {
        private final Rectangle popupRect = new Rectangle();
        private boolean popupVisible;
    }

    private static final class FrameSlot {
        private ByteBuffer buffer;
        private int width;
        private int height;
        private long frameVersion;
    }

    private record MainCapturePlan(
            int writeIndex,
            long frameVersion,
            ByteBuffer targetBuffer,
            GrapheneDirtyRegion damageToCopy
    ) {
    }
}
