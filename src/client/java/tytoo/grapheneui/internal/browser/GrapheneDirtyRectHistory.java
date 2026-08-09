package tytoo.grapheneui.internal.browser;

import java.util.ArrayDeque;
import java.util.Deque;

/** Retains enough per-paint damage to reconstruct skipped Chromium frame deltas safely. */
final class GrapheneDirtyRectHistory {
    static final int DEFAULT_CAPACITY = 120;

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    GrapheneDirtyRectHistory() {
        this(DEFAULT_CAPACITY);
    }

    GrapheneDirtyRectHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    void add(long version, int width, int height, GrapheneDirtyRegion damage) {
        Entry latest = entries.peekLast();
        if (latest != null
                && (latest.version + 1L != version || latest.width != width || latest.height != height)) {
            entries.clear();
        }

        entries.addLast(new Entry(version, width, height, damage));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    GrapheneDirtyRegion damageSince(long baseVersion, long targetVersion, int width, int height) {
        if (baseVersion == targetVersion) {
            return GrapheneDirtyRegion.empty();
        }
        if (baseVersion < 0L || baseVersion > targetVersion || entries.isEmpty()) {
            return GrapheneDirtyRegion.historyFallback();
        }

        long expectedVersion = baseVersion + 1L;
        GrapheneDirtyRegion accumulated = GrapheneDirtyRegion.empty();
        for (Entry entry : entries) {
            if (entry.version <= baseVersion) {
                continue;
            }
            if (entry.version != expectedVersion || entry.width != width || entry.height != height) {
                return GrapheneDirtyRegion.historyFallback();
            }

            accumulated = accumulated.merge(entry.damage, width, height);
            if (entry.version == targetVersion) {
                return accumulated;
            }
            if (entry.version > targetVersion) {
                break;
            }
            expectedVersion++;
        }

        return GrapheneDirtyRegion.historyFallback();
    }

    private record Entry(long version, int width, int height, GrapheneDirtyRegion damage) {
    }
}
