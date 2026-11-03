package tytoo.grapheneui.internal.cef;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.url.GrapheneAppUrls;

import java.util.concurrent.TimeUnit;

/**
 * Opens and closes one small hidden page right after CEF starts, so the player's first screen is as fast as every
 * later one. The first browser of a session pays for starting a renderer process and for loading Graphene's classes on
 * the render thread. Once a page has loaded, Chromium keeps a spare renderer ready for the next navigation, so no
 * browser has to stay open: the spare is the only thing that remains, and it would appear after the first screen anyway.
 */
final class GrapheneSurfaceWarmup {
    static final String ENABLED_PROPERTY = "graphene.warmup";
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneSurfaceWarmup.class);
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneSurfaceWarmup.class);
    static final String PAGE = "warmup/warmup.html";
    private static final int SIZE = 16;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static boolean started;
    private static BrowserSurface surface;
    private static long startedAtNanos;

    private GrapheneSurfaceWarmup() {
    }

    static void start() {
        if (started || !Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            return;
        }

        started = true;
        try {
            startedAtNanos = System.nanoTime();
            surface = BrowserSurface.builder()
                    .url(GrapheneAppUrls.asset(GrapheneCore.ID, PAGE))
                    .surfaceSize(SIZE, SIZE)
                    .resolution(SIZE, SIZE)
                    .owner(GrapheneSurfaceWarmup.class)
                    .build();
            ClientTickEvents.END_CLIENT_TICK.register(ignoredClient -> tick());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to warm up Graphene; the first screen will open more slowly", exception);
            surface = null;
        }
    }

    private static void tick() {
        BrowserSurface activeSurface = surface;
        if (activeSurface == null) {
            return;
        }

        try {
            boolean drawn = activeSurface.prepareTextureFrame() != null && activeSurface.hasVisibleContent();
            if ((drawn && activeSurface.bridge().isReady()) || System.nanoTime() - startedAtNanos >= TIMEOUT_NANOS) {
                finish(activeSurface);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Graphene warm-up failed", exception);
            finish(activeSurface);
        }
    }

    private static void finish(BrowserSurface activeSurface) {
        surface = null;
        DEBUG_LOGGER.debug(
                "Graphene warm-up finished after {} ms",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
        activeSurface.close();
    }
}
