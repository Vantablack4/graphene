package tytoo.grapheneui.internal.cef;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import tytoo.grapheneui.internal.browser.GrapheneBrowserSurfaceManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefRuntimeReadinessTest {
    @Test
    void readinessProbeDoesNotWaitForTheLifecycleMonitor() throws Exception {
        GrapheneCefRuntime runtime = new GrapheneCefRuntime(new GrapheneBrowserSurfaceManager());
        Field lockField = GrapheneCefRuntime.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        Object lifecycleLock = lockField.get(runtime);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Thread lockHolder = Thread.ofPlatform().start(() -> {
            synchronized (lifecycleLock) {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        assertTrue(lockHeld.await(2, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(Duration.ofMillis(200), () -> assertFalse(runtime.isInitialized()));
        } finally {
            releaseLock.countDown();
            lockHolder.join(2_000L);
        }
    }
}
