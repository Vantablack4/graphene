package tytoo.grapheneui.internal.cef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.mc.McClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class GrapheneCefFrameworkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneCefFrameworkLoader.class);
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneCefFrameworkLoader.class);
    private static final String FRAMEWORK_BUNDLE_NAME = "Chromium Embedded Framework.framework";
    private static final String FRAMEWORK_BINARY_NAME = "Chromium Embedded Framework";
    private static final String HELPER_EXECUTABLE_PATH = "jcef Helper.app/Contents/MacOS/jcef Helper";
    private static final long HELPER_TIMEOUT_SECONDS = 60L;
    private static final long RENDER_THREAD_POLL_MILLIS = 100L;
    private static final long CLIENT_LOADING_RETRY_MILLIS = 250L;
    private static final long SLOW_RENDER_THREAD_LOAD_MILLIS = 250L;
    private static final Duration MAX_CLIENT_LOADING_WAIT = Duration.ofMinutes(2);
    private static final String SHUTTING_DOWN_MESSAGE = "Graphene CEF runtime is shutting down";

    private final Path installPath;
    private final Executor renderThreadExecutor;
    private final Consumer<String> libraryLoader;
    private final BooleanSupplier clientLoading;
    private final BooleanSupplier stopping;
    private final Duration maxClientLoadingWait;

    GrapheneCefFrameworkLoader(
            Path installPath,
            Executor renderThreadExecutor,
            Consumer<String> libraryLoader,
            BooleanSupplier clientLoading,
            BooleanSupplier stopping,
            Duration maxClientLoadingWait
    ) {
        this.installPath = Objects.requireNonNull(installPath, "installPath");
        this.renderThreadExecutor = Objects.requireNonNull(renderThreadExecutor, "renderThreadExecutor");
        this.libraryLoader = Objects.requireNonNull(libraryLoader, "libraryLoader");
        this.clientLoading = Objects.requireNonNull(clientLoading, "clientLoading");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.maxClientLoadingWait = Objects.requireNonNull(maxClientLoadingWait, "maxClientLoadingWait");
    }

    static GrapheneCefFrameworkLoader forRenderThread(Path installPath, BooleanSupplier stopping) {
        return new GrapheneCefFrameworkLoader(
                installPath,
                McClient::runOnMainThread,
                System::load,
                () -> McClient.currentOverlay() != null,
                stopping,
                MAX_CLIENT_LOADING_WAIT
        );
    }

    static Path frameworkBinary(Path installPath) {
        return installPath.resolve(FRAMEWORK_BUNDLE_NAME).resolve(FRAMEWORK_BINARY_NAME);
    }

    static Path helperExecutable(Path installPath) {
        return installPath.resolve(HELPER_EXECUTABLE_PATH);
    }

    void load() {
        Path frameworkBinary = frameworkBinary(installPath);
        if (!Files.isRegularFile(frameworkBinary)) {
            DEBUG_LOGGER.debug("Skipping render-thread CEF framework load because {} is missing", frameworkBinary);
            return;
        }

        long warmUpNanos = warmUpLibraryValidation();
        long waitStartNanos = System.nanoTime();
        long loadingDeadlineNanos = waitStartNanos + maxClientLoadingWait.toNanos();
        try {
            OptionalLong loadNanos = loadOnRenderThread(frameworkBinary, loadingDeadlineNanos);
            while (loadNanos.isEmpty()) {
                pauseWhileClientLoads();
                loadNanos = loadOnRenderThread(frameworkBinary, loadingDeadlineNanos);
            }

            long waitNanos = System.nanoTime() - waitStartNanos - loadNanos.getAsLong();
            logRenderThreadLoad(loadNanos.getAsLong(), waitNanos, warmUpNanos);
        } catch (ExecutionException exception) {
            LOGGER.warn(
                    "Failed to load the CEF framework on the render thread; CEF loads it on the startup thread instead",
                    exception.getCause()
            );
        } catch (CancellationException exception) {
            throw new IllegalStateException(SHUTTING_DOWN_MESSAGE, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the render thread to load CEF", exception);
        }
    }

    private long warmUpLibraryValidation() {
        Path helperExecutable = helperExecutable(installPath);
        if (!Files.isExecutable(helperExecutable)) {
            return 0L;
        }

        long startNanos = System.nanoTime();
        try {
            Process helper = new ProcessBuilder(helperExecutable.toString())
                    .directory(installPath.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            helper.getOutputStream().close();
            if (helper.waitFor(HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                DEBUG_LOGGER.debug("CEF helper library validation warm-up exited with code {}", helper.exitValue());
            } else {
                helper.destroyForcibly();
                LOGGER.warn("CEF helper did not exit within {} s while warming up library validation", HELPER_TIMEOUT_SECONDS);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to warm up CEF library validation; the render thread will validate the framework", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while warming up CEF library validation", exception);
        }

        return System.nanoTime() - startNanos;
    }

    private OptionalLong loadOnRenderThread(Path frameworkBinary, long loadingDeadlineNanos)
            throws ExecutionException, InterruptedException {
        boolean deferWhileClientLoads = System.nanoTime() < loadingDeadlineNanos;
        CompletableFuture<OptionalLong> renderThreadLoad = new CompletableFuture<>();
        renderThreadExecutor.execute(() -> loadLibrary(frameworkBinary, deferWhileClientLoads, renderThreadLoad));
        return waitForRenderThread(renderThreadLoad);
    }

    private void loadLibrary(
            Path frameworkBinary,
            boolean deferWhileClientLoads,
            CompletableFuture<OptionalLong> renderThreadLoad
    ) {
        if (renderThreadLoad.isDone() || stopping.getAsBoolean()) {
            renderThreadLoad.cancel(false);
            return;
        }

        if (deferWhileClientLoads && clientLoading.getAsBoolean()) {
            renderThreadLoad.complete(OptionalLong.empty());
            return;
        }

        long startNanos = System.nanoTime();
        try {
            libraryLoader.accept(frameworkBinary.toString());
            renderThreadLoad.complete(OptionalLong.of(System.nanoTime() - startNanos));
        } catch (RuntimeException | LinkageError exception) {
            renderThreadLoad.completeExceptionally(exception);
        }
    }

    private OptionalLong waitForRenderThread(CompletableFuture<OptionalLong> renderThreadLoad)
            throws ExecutionException, InterruptedException {
        while (true) {
            try {
                return renderThreadLoad.get(RENDER_THREAD_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // The render thread has not drained its task queue yet; keep waiting unless the client is stopping.
                if (stopping.getAsBoolean()) {
                    renderThreadLoad.cancel(false);
                    throw new IllegalStateException(SHUTTING_DOWN_MESSAGE);
                }
            }
        }
    }

    private void pauseWhileClientLoads() throws InterruptedException {
        if (stopping.getAsBoolean()) {
            throw new IllegalStateException(SHUTTING_DOWN_MESSAGE);
        }

        Thread.sleep(CLIENT_LOADING_RETRY_MILLIS);
    }

    private void logRenderThreadLoad(long loadNanos, long waitNanos, long warmUpNanos) {
        long loadMillis = TimeUnit.NANOSECONDS.toMillis(loadNanos);
        long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitNanos);
        long warmUpMillis = TimeUnit.NANOSECONDS.toMillis(warmUpNanos);
        if (loadMillis > SLOW_RENDER_THREAD_LOAD_MILLIS) {
            LOGGER.warn(
                    "Loading the CEF framework held the render thread for {} ms (waited {} ms for loading, library validation warm-up {} ms)",
                    loadMillis,
                    waitMillis,
                    warmUpMillis
            );
            return;
        }

        LOGGER.info(
                "Loaded the CEF framework on the render thread in {} ms (waited {} ms for loading, library validation warm-up {} ms)",
                loadMillis,
                waitMillis,
                warmUpMillis
        );
    }
}
