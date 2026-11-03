package tytoo.grapheneui.internal.browser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timeline of a surface opening, from browser creation to the first drawn frame that shows content. Inert unless a
 * test installs a sink. Everything happens on whichever CEF or render thread reports the event.
 */
public final class GrapheneOpenTrace {
    private static final Map<Object, BrowserState> STATES = new ConcurrentHashMap<>();
    private static volatile Sink sink;

    private GrapheneOpenTrace() {
    }

    public static void install(Sink newSink) {
        STATES.clear();
        sink = newSink;
    }

    public static boolean isActive() {
        return sink != null;
    }

    public static void mark(Object browser, String name, String detail) {
        Sink activeSink = sink;
        if (activeSink != null) {
            activeSink.mark(browser, name, detail, System.nanoTime());
        }
    }

    static void paint(Object browser, ByteBuffer buffer, int width, int height, long version) {
        Sink activeSink = sink;
        if (activeSink == null) {
            return;
        }

        long now = System.nanoTime();
        BrowserState state = STATES.computeIfAbsent(browser, ignoredBrowser -> new BrowserState());
        if (!state.painted) {
            state.painted = true;
            activeSink.mark(browser, "paint.first", width + "x" + height, now);
        }

        if (state.contentVersion < 0L && hasVisiblePixel(buffer)) {
            state.contentVersion = version;
            activeSink.mark(browser, "paint.content", width + "x" + height + " v" + version, now);
        }
    }

    static void drawn(Object browser, long version) {
        Sink activeSink = sink;
        if (activeSink == null || version < 0L) {
            return;
        }

        long now = System.nanoTime();
        BrowserState state = STATES.computeIfAbsent(browser, ignoredBrowser -> new BrowserState());
        if (!state.drawn) {
            state.drawn = true;
            activeSink.mark(browser, "frame.first", "v" + version, now);
        }

        if (!state.drawnContent && state.contentVersion >= 0L && version >= state.contentVersion) {
            state.drawnContent = true;
            activeSink.mark(browser, "frame.content", "v" + version, now);
        }
    }

    private static boolean hasVisiblePixel(ByteBuffer buffer) {
        IntBuffer pixels = buffer.duplicate().order(ByteOrder.nativeOrder()).position(0).asIntBuffer();
        while (pixels.hasRemaining()) {
            if (pixels.get() != 0) {
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    public interface Sink {
        void mark(Object browser, String name, String detail, long nanos);
    }

    private static final class BrowserState {
        private volatile boolean painted;
        private volatile boolean drawn;
        private volatile boolean drawnContent;
        private volatile long contentVersion = -1L;
    }
}
