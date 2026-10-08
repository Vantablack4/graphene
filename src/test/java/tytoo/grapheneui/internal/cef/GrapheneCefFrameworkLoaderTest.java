package tytoo.grapheneui.internal.cef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisabledOnOs(OS.WINDOWS)
final class GrapheneCefFrameworkLoaderTest {
    private static final String RENDER_THREAD_NAME = "graphene-test-render";
    private static final Duration LOADING_WAIT = Duration.ofSeconds(30);

    @TempDir
    Path installPath;

    @Test
    void frameworkBinaryMatchesThePathJcefLoads() {
        String jcefFrameworkDirectory = installPath.toFile().getAbsolutePath() + "/Chromium Embedded Framework.framework";

        assertEquals(
                jcefFrameworkDirectory + "/Chromium Embedded Framework",
                GrapheneCefFrameworkLoader.frameworkBinary(installPath).toString()
        );
    }

    @Test
    void loadsTheFrameworkOnTheRenderThreadAfterWarmingUpLibraryValidation() throws Exception {
        Path frameworkBinary = createFrameworkBinary();
        createHelperThatLeavesMarker();
        List<String> loads = new CopyOnWriteArrayList<>();
        ExecutorService renderThread = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, RENDER_THREAD_NAME));
        try {
            GrapheneCefFrameworkLoader loader = loader(
                    renderThread,
                    path -> loads.add(Thread.currentThread().getName() + "|" + path + "|" + Files.exists(installPath.resolve("warmed"))),
                    () -> false,
                    () -> false,
                    LOADING_WAIT
            );

            assertTimeoutPreemptively(Duration.ofSeconds(10), loader::load);
        } finally {
            renderThread.shutdownNow();
        }

        assertEquals(List.of(RENDER_THREAD_NAME + "|" + frameworkBinary + "|true"), loads);
    }

    @Test
    void waitsForTheClientToFinishLoading() throws IOException {
        createFrameworkBinary();
        AtomicInteger loadingChecks = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger();
        GrapheneCefFrameworkLoader loader = loader(
                Runnable::run,
                ignoredPath -> loads.incrementAndGet(),
                () -> loadingChecks.incrementAndGet() <= 2,
                () -> false,
                LOADING_WAIT
        );

        assertTimeoutPreemptively(Duration.ofSeconds(10), loader::load);

        assertEquals(3, loadingChecks.get());
        assertEquals(1, loads.get());
    }

    @Test
    void loadsWhileTheClientLoadsOnceTheWaitRunsOut() throws IOException {
        createFrameworkBinary();
        AtomicInteger loads = new AtomicInteger();
        GrapheneCefFrameworkLoader loader = loader(
                Runnable::run,
                ignoredPath -> loads.incrementAndGet(),
                () -> true,
                () -> false,
                Duration.ZERO
        );

        assertTimeoutPreemptively(Duration.ofSeconds(10), loader::load);

        assertEquals(1, loads.get());
    }

    @Test
    void keepsStartingWhenTheRenderThreadLoadFails() throws IOException {
        createFrameworkBinary();
        GrapheneCefFrameworkLoader loader = loader(
                Runnable::run,
                ignoredPath -> {
                    throw new UnsatisfiedLinkError("not a library");
                },
                () -> false,
                () -> false,
                LOADING_WAIT
        );

        assertDoesNotThrow(loader::load);
    }

    @Test
    void stopsWaitingWhenTheClientStops() throws IOException {
        createFrameworkBinary();
        GrapheneCefFrameworkLoader loader = loader(
                ignoredTask -> {
                },
                ignoredPath -> {
                },
                () -> false,
                () -> true,
                LOADING_WAIT
        );

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(IllegalStateException.class, loader::load));
    }

    @Test
    void skipsTheQueuedLoadOnceTheClientStopped() throws IOException {
        createFrameworkBinary();
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        AtomicBoolean loaded = new AtomicBoolean();
        GrapheneCefFrameworkLoader loader = loader(
                queuedTask::set,
                ignoredPath -> loaded.set(true),
                () -> false,
                () -> true,
                LOADING_WAIT
        );

        assertThrows(IllegalStateException.class, loader::load);
        queuedTask.get().run();

        assertFalse(loaded.get());
    }

    @Test
    void skipsTheRenderThreadWhenTheFrameworkIsMissing() {
        AtomicBoolean scheduled = new AtomicBoolean();
        GrapheneCefFrameworkLoader loader = loader(
                ignoredTask -> scheduled.set(true),
                ignoredPath -> {
                },
                () -> false,
                () -> false,
                LOADING_WAIT
        );

        loader.load();

        assertFalse(scheduled.get());
    }

    private GrapheneCefFrameworkLoader loader(
            Executor renderThreadExecutor,
            Consumer<String> libraryLoader,
            BooleanSupplier clientLoading,
            BooleanSupplier stopping,
            Duration maxClientLoadingWait
    ) {
        return new GrapheneCefFrameworkLoader(
                installPath,
                renderThreadExecutor,
                libraryLoader,
                clientLoading,
                stopping,
                maxClientLoadingWait
        );
    }

    private Path createFrameworkBinary() throws IOException {
        Path frameworkBinary = GrapheneCefFrameworkLoader.frameworkBinary(installPath);
        Files.createDirectories(frameworkBinary.getParent());
        return Files.writeString(frameworkBinary, "framework");
    }

    private void createHelperThatLeavesMarker() throws IOException {
        Path helperExecutable = GrapheneCefFrameworkLoader.helperExecutable(installPath);
        Files.createDirectories(helperExecutable.getParent());
        Files.writeString(helperExecutable, "#!/bin/sh\necho warmed > warmed\n");
        assertTrue(helperExecutable.toFile().setExecutable(true));
        assumeTrue(Files.isExecutable(helperExecutable), "temporary directory does not allow executables");
    }
}
