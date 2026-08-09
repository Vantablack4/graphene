package tytoo.grapheneui.internal.browser;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable, clamped browser damage. A full region is used whenever partial damage cannot be
 * proven complete.
 */
final class GrapheneDirtyRegion {
    static final int MAX_PARTIAL_RECTS = 64;

    private static final GrapheneDirtyRegion EMPTY = new GrapheneDirtyRegion(new Rectangle[0], false, false);
    private static final GrapheneDirtyRegion FULL = new GrapheneDirtyRegion(null, true, false);
    private static final GrapheneDirtyRegion HISTORY_FALLBACK = new GrapheneDirtyRegion(null, true, true);
    private static final Comparator<Rectangle> RECT_ORDER = Comparator
            .comparingInt((Rectangle rectangle) -> rectangle.y)
            .thenComparingInt(rectangle -> rectangle.x)
            .thenComparingInt(rectangle -> rectangle.height)
            .thenComparingInt(rectangle -> rectangle.width);

    private final Rectangle[] rectangles;
    private final boolean full;
    private final boolean historyFallback;

    private GrapheneDirtyRegion(Rectangle[] rectangles, boolean full, boolean historyFallback) {
        this.rectangles = rectangles;
        this.full = full;
        this.historyFallback = historyFallback;
    }

    static GrapheneDirtyRegion empty() {
        return EMPTY;
    }

    static GrapheneDirtyRegion full() {
        return FULL;
    }

    static GrapheneDirtyRegion historyFallback() {
        return HISTORY_FALLBACK;
    }

    static GrapheneDirtyRegion from(Rectangle[] dirtyRects, int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0 || dirtyRects == null || dirtyRects.length == 0) {
            return FULL;
        }

        List<Rectangle> clampedRects = new ArrayList<>(Math.min(dirtyRects.length, MAX_PARTIAL_RECTS));
        for (Rectangle dirtyRect : dirtyRects) {
            Rectangle clampedRect = clamp(dirtyRect, frameWidth, frameHeight);
            if (clampedRect == null) {
                continue;
            }

            if (clampedRect.x == 0
                    && clampedRect.y == 0
                    && clampedRect.width == frameWidth
                    && clampedRect.height == frameHeight) {
                return FULL;
            }

            if (!addDisjoint(clampedRects, clampedRect)) {
                return FULL;
            }
            coalesceAdjacent(clampedRects);
            if (clampedRects.size() >= MAX_PARTIAL_RECTS) {
                return FULL;
            }
        }

        if (clampedRects.isEmpty()) {
            return FULL;
        }

        clampedRects.sort(RECT_ORDER);
        return new GrapheneDirtyRegion(clampedRects.toArray(Rectangle[]::new), false, false);
    }

    GrapheneDirtyRegion merge(GrapheneDirtyRegion other, int frameWidth, int frameHeight) {
        if (full || other.full) {
            return historyFallback || other.historyFallback ? HISTORY_FALLBACK : FULL;
        }

        if (rectangles.length == 0) {
            return other;
        }
        if (other.rectangles.length == 0) {
            return this;
        }

        Rectangle[] combined = new Rectangle[rectangles.length + other.rectangles.length];
        System.arraycopy(rectangles, 0, combined, 0, rectangles.length);
        System.arraycopy(other.rectangles, 0, combined, rectangles.length, other.rectangles.length);
        return from(combined, frameWidth, frameHeight);
    }

    boolean isFull() {
        return full;
    }

    boolean isHistoryFallback() {
        return historyFallback;
    }

    Rectangle[] copyRectangles() {
        if (rectangles == null) {
            return null;
        }

        Rectangle[] copy = new Rectangle[rectangles.length];
        for (int index = 0; index < rectangles.length; index++) {
            copy[index] = new Rectangle(rectangles[index]);
        }
        return copy;
    }

    long pixelCount(int frameWidth, int frameHeight) {
        if (full) {
            return Math.max(0L, (long) frameWidth * frameHeight);
        }

        long pixels = 0L;
        for (Rectangle rectangle : rectangles) {
            pixels += (long) rectangle.width * rectangle.height;
        }
        return pixels;
    }

    private static Rectangle clamp(Rectangle dirtyRect, int frameWidth, int frameHeight) {
        if (dirtyRect == null || dirtyRect.width <= 0 || dirtyRect.height <= 0) {
            return null;
        }

        return GrapheneBrowserRenderBounds.intersect(
                dirtyRect.x,
                dirtyRect.y,
                dirtyRect.width,
                dirtyRect.height,
                0,
                0,
                frameWidth,
                frameHeight
        );
    }

    private static boolean addDisjoint(List<Rectangle> rectangles, Rectangle candidate) {
        List<Rectangle> uncovered = new ArrayList<>();
        uncovered.add(new Rectangle(candidate));
        for (Rectangle existing : rectangles) {
            List<Rectangle> next = new ArrayList<>();
            for (Rectangle rectangle : uncovered) {
                subtract(rectangle, existing, next);
                if (rectangles.size() + next.size() >= MAX_PARTIAL_RECTS) {
                    return false;
                }
            }
            uncovered = next;
            if (uncovered.isEmpty()) {
                return true;
            }
        }

        rectangles.addAll(uncovered);
        return rectangles.size() < MAX_PARTIAL_RECTS;
    }

    private static void subtract(Rectangle source, Rectangle cover, List<Rectangle> target) {
        Rectangle intersection = source.intersection(cover);
        if (intersection.width <= 0 || intersection.height <= 0) {
            target.add(source);
            return;
        }

        addIfNonEmpty(target, source.x, source.y, source.width, intersection.y - source.y);
        addIfNonEmpty(
                target,
                source.x,
                intersection.y + intersection.height,
                source.width,
                source.y + source.height - intersection.y - intersection.height
        );
        addIfNonEmpty(target, source.x, intersection.y, intersection.x - source.x, intersection.height);
        addIfNonEmpty(
                target,
                intersection.x + intersection.width,
                intersection.y,
                source.x + source.width - intersection.x - intersection.width,
                intersection.height
        );
    }

    private static void addIfNonEmpty(List<Rectangle> rectangles, int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            rectangles.add(new Rectangle(x, y, width, height));
        }
    }

    private static void coalesceAdjacent(List<Rectangle> rectangles) {
        boolean changed;
        do {
            changed = false;
            for (int leftIndex = 0; leftIndex < rectangles.size(); leftIndex++) {
                Rectangle left = rectangles.get(leftIndex);
                for (int rightIndex = leftIndex + 1; rightIndex < rectangles.size(); rightIndex++) {
                    Rectangle right = rectangles.get(rightIndex);
                    Rectangle merged = mergeIfRectangular(left, right);
                    if (merged == null) {
                        continue;
                    }

                    rectangles.set(leftIndex, merged);
                    rectangles.remove(rightIndex);
                    changed = true;
                    break;
                }
                if (changed) {
                    break;
                }
            }
        } while (changed);
    }

    private static Rectangle mergeIfRectangular(Rectangle left, Rectangle right) {
        if (left.y == right.y && left.height == right.height) {
            if (left.x + left.width == right.x || right.x + right.width == left.x) {
                return left.union(right);
            }
        }

        if (left.x == right.x && left.width == right.width) {
            if (left.y + left.height == right.y || right.y + right.height == left.y) {
                return left.union(right);
            }
        }

        return null;
    }
}
