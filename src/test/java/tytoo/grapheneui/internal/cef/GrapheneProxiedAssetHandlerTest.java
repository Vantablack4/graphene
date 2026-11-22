package tytoo.grapheneui.internal.cef;

import org.cef.misc.IntRef;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneProxiedAssetHandlerTest {
    private static final String URL = "https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json";
    private static final Duration LONG = Duration.ofMinutes(1);
    private static final ScheduledExecutorService TIMER = GrapheneProxiedAssetHandler.timer();

    @Test
    void onlyAnUpstreamOkIsPassedOnEverythingElseIsALocalError() {
        assertEquals(200, GrapheneProxiedAssetHandler.localStatusFor(200));
        assertEquals(404, GrapheneProxiedAssetHandler.localStatusFor(404));
        for (int upstream : new int[]{201, 204, 206, 301, 302, 304, 307, 401, 403, 410, 500, 503}) {
            assertEquals(502, GrapheneProxiedAssetHandler.localStatusFor(upstream), "upstream " + upstream);
        }
    }

    @Test
    void aBodyIsFetchedOnAWorkerAndAnsweredFromIt() throws Exception {
        Fixture fixture = new Fixture(1, 4, 1024);
        AtomicReference<String> openThread = new AtomicReference<>();
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = fixture.handler((url, deadline) -> {
            openThread.set(Thread.currentThread().getName());
            return new GrapheneProxiedAssetHandler.Upstream(200, 3, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        });

        assertTrue(handler.start("GET", probe));

        probe.await();
        assertEquals(200, handler.status());
        assertTrue(openThread.get().startsWith(GrapheneProxiedAssetHandler.WORKER_THREAD_NAME), openThread.get());
        assertTrue(probe.thread.get().startsWith(GrapheneProxiedAssetHandler.WORKER_THREAD_NAME), probe.thread.get());
        assertEquals(3, fixture.budget.usedBytes());
        assertArrayEquals(new byte[]{1, 2, 3}, drain(handler));
        assertEquals(0, fixture.budget.usedBytes());
    }

    @Test
    void localRefusalsAreAnsweredFromTheTimerNeverFromTheCallingThread() throws Exception {
        Fixture fixture = new Fixture(1, 1, 1024);
        Probe posted = new Probe();
        fixture.handler((url, deadline) -> {
            throw new AssertionError("a POST reached upstream");
        }).start("POST", posted);
        posted.await();
        assertTrue(posted.thread.get().startsWith(GrapheneProxiedAssetHandler.TIMER_THREAD_NAME), posted.thread.get());

        BlockingStream holding = new BlockingStream();
        fixture.handler((url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 10, holding)).start("GET", new Probe());
        eventually(() -> holding.reading.getCount() == 0);
        fixture.handler((url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 1, new ByteArrayInputStream(new byte[]{1})))
                .start("GET", new Probe());
        Probe refused = new Probe();
        GrapheneProxiedAssetHandler full = fixture.handler((url, deadline) -> {
            throw new AssertionError("a request beyond the queue was fetched");
        });
        full.start("GET", refused);
        refused.await();
        assertEquals(503, full.status());
        assertTrue(refused.thread.get().startsWith(GrapheneProxiedAssetHandler.TIMER_THREAD_NAME), refused.thread.get());
        holding.close();
    }

    @Test
    void aTimedOutFetchClosesItsStreamBeforeItsWorkerTakesTheNextRequest() throws Exception {
        Fixture fixture = new Fixture(1, 4, 1024, Duration.ofMillis(150), LONG);
        List<String> order = new ArrayList<>();
        BlockingStream stalled = new BlockingStream(() -> record(order, "first stream closed"));
        Probe first = new Probe();
        GrapheneProxiedAssetHandler timedOut = fixture.handler((url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 100, stalled));
        timedOut.start("GET", first);
        Probe second = new Probe();
        fixture.handler((url, deadline) -> {
            record(order, "second fetch started");
            return new GrapheneProxiedAssetHandler.Upstream(200, 1, new ByteArrayInputStream(new byte[]{1}));
        }).start("GET", second);

        first.await();
        second.await();
        assertEquals(504, timedOut.status());
        assertEquals(List.of("first stream closed", "second fetch started"), snapshot(order));
        assertFalse(timedOut.holdsBody());
    }

    @Test
    void theBodyIsDroppedAsSoonAsCefCopiesItsLastByte() throws Exception {
        Fixture fixture = new Fixture(1, 1, 1024);
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 4, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}))
        );
        handler.start("GET", probe);
        probe.await();

        byte[] buffer = new byte[4];
        IntRef read = new IntRef();
        assertTrue(handler.readResponse(buffer, 4, read, null));
        assertEquals(4, read.get());
        assertFalse(handler.holdsBody(), "the body stayed after its last byte was copied");
        assertEquals(0, fixture.budget.usedBytes());
        assertFalse(handler.readResponse(buffer, 4, read, null));
    }

    @Test
    void aBodyCefStopsReadingIsDroppedAtTheDeliveryDeadline() throws Exception {
        Fixture fixture = new Fixture(1, 1, 1024, LONG, Duration.ofMillis(150));
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 4, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}))
        );
        handler.start("GET", probe);
        probe.await();
        byte[] buffer = new byte[1];
        handler.readResponse(buffer, 1, new IntRef(), null);
        assertTrue(handler.holdsBody());

        eventually(() -> !handler.holdsBody());
        assertEquals(0, fixture.budget.usedBytes());
        assertFalse(handler.readResponse(buffer, 1, new IntRef(), null));
    }

    @Test
    void aCancelWhileQueuedNeverFetchesAndACancelWhileReadingClosesTheStream() throws Exception {
        Fixture fixture = new Fixture(1, 4, 1024);
        BlockingStream reading = new BlockingStream();
        Probe readingProbe = new Probe();
        GrapheneProxiedAssetHandler running = fixture.handler((url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 100, reading));
        running.start("GET", readingProbe);
        eventually(() -> reading.reading.getCount() == 0);

        AtomicInteger queuedOpens = new AtomicInteger();
        Probe queuedProbe = new Probe();
        GrapheneProxiedAssetHandler queued = fixture.handler((url, deadline) -> {
            queuedOpens.incrementAndGet();
            return new GrapheneProxiedAssetHandler.Upstream(200, 1, new ByteArrayInputStream(new byte[]{1}));
        });
        queued.start("GET", queuedProbe);
        queued.cancel();
        assertEquals(0, fixture.pool.getQueue().size(), "the cancelled request kept its place in the queue");

        running.cancel();
        eventually(() -> reading.closed && fixture.pool.getActiveCount() == 0);
        Thread.sleep(50);
        assertEquals(0, queuedOpens.get());
        assertEquals(0, readingProbe.calls.get());
        assertEquals(0, queuedProbe.calls.get());
        assertEquals(0, fixture.budget.usedBytes());
    }

    @Test
    void sizesAreCheckedAtTheHeadersAndBodiesAreBudgeted() throws Exception {
        Fixture fixture = new Fixture(1, 4, 10);
        CountingStream declaredTooLarge = new CountingStream(new byte[0]);
        Probe tooLarge = new Probe();
        GrapheneProxiedAssetHandler large = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, GrapheneProxiedAssetHandler.MAX_BODY_BYTES + 1L, declaredTooLarge)
        );
        large.start("GET", tooLarge);
        tooLarge.await();
        assertEquals(502, large.status());
        assertEquals(0, declaredTooLarge.reads.get(), "a body declared too large was read");
        assertTrue(declaredTooLarge.closed);

        CountingStream notOk = new CountingStream(new byte[]{1, 2});
        Probe missingProbe = new Probe();
        GrapheneProxiedAssetHandler missing = fixture.handler((url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(404, 2, notOk));
        missing.start("GET", missingProbe);
        missingProbe.await();
        assertEquals(404, missing.status());
        assertEquals(0, notOk.reads.get());
        assertTrue(notOk.closed);

        Probe overBudget = new Probe();
        GrapheneProxiedAssetHandler budgeted = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 11, new ByteArrayInputStream(new byte[11]))
        );
        budgeted.start("GET", overBudget);
        overBudget.await();
        assertEquals(503, budgeted.status());

        Probe shortProbe = new Probe();
        GrapheneProxiedAssetHandler truncated = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, 5, new ByteArrayInputStream(new byte[]{1, 2}))
        );
        truncated.start("GET", shortProbe);
        shortProbe.await();
        assertEquals(502, truncated.status());
        assertEquals(0, fixture.budget.usedBytes());
    }

    @Test
    void anUnknownLengthIsReadUpToTheCapIntoAnExactArray() throws Exception {
        Fixture fixture = new Fixture(1, 4, 64L * 1024 * 1024);
        byte[] content = new byte[100_000];
        content[99_999] = 7;
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, -1, new ByteArrayInputStream(content))
        );
        handler.start("GET", probe);
        probe.await();
        assertEquals(200, handler.status());
        assertEquals(content.length, fixture.budget.usedBytes(), "the reservation was not trimmed to the body");
        assertArrayEquals(content, drain(handler));

        Probe tooLargeProbe = new Probe();
        GrapheneProxiedAssetHandler tooLarge = fixture.handler(
                (url, deadline) -> new GrapheneProxiedAssetHandler.Upstream(200, -1, new ByteArrayInputStream(new byte[GrapheneProxiedAssetHandler.MAX_BODY_BYTES + 1]))
        );
        tooLarge.start("GET", tooLargeProbe);
        tooLargeProbe.await();
        assertEquals(502, tooLarge.status());
        assertEquals(0, fixture.budget.usedBytes());
    }

    @Test
    void aThumbnailGridNeverGets503() throws Exception {
        Fixture fixture = new Fixture(
                GrapheneProxiedAssetHandler.WORKERS,
                GrapheneProxiedAssetHandler.MAX_QUEUED,
                GrapheneProxiedAssetHandler.BUDGET_BYTES
        );
        List<GrapheneProxiedAssetHandler> handlers = new ArrayList<>();
        List<Probe> probes = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            byte[] tile = new byte[2048];
            GrapheneProxiedAssetHandler handler = fixture.handler((url, deadline) -> {
                Thread.sleep(5);
                return new GrapheneProxiedAssetHandler.Upstream(200, tile.length, new ByteArrayInputStream(tile));
            });
            Probe probe = new Probe(handler);
            handlers.add(handler);
            probes.add(probe);
            handler.start("GET", probe);
        }

        for (Probe probe : probes) {
            probe.await();
        }

        for (GrapheneProxiedAssetHandler handler : handlers) {
            assertEquals(200, handler.status());
        }
        assertEquals(0, fixture.budget.usedBytes());
    }

    private static byte[] drain(GrapheneProxiedAssetHandler handler) {
        List<Byte> bytes = new ArrayList<>();
        byte[] buffer = new byte[3];
        IntRef read = new IntRef();
        while (handler.readResponse(buffer, buffer.length, read, null)) {
            for (int index = 0; index < read.get(); index++) {
                bytes.add(buffer[index]);
            }
        }

        byte[] result = new byte[bytes.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = bytes.get(index);
        }
        return result;
    }

    private static void record(List<String> order, String event) {
        synchronized (order) {
            order.add(event);
        }
    }

    private static List<String> snapshot(List<String> order) {
        synchronized (order) {
            return List.copyOf(order);
        }
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 500 && !condition.getAsBoolean(); attempt++) {
            Thread.sleep(10);
        }

        assertTrue(condition.getAsBoolean(), "a condition never became true");
    }

    private static final class Fixture {
        private final ThreadPoolExecutor pool;
        private final GrapheneProxyBodyBudget budget;
        private final Duration deadline;
        private final Duration deliveryDeadline;

        private Fixture(int workers, int maxQueued, long budgetBytes) {
            this(workers, maxQueued, budgetBytes, LONG, LONG);
        }

        private Fixture(int workers, int maxQueued, long budgetBytes, Duration deadline, Duration deliveryDeadline) {
            this.pool = GrapheneProxiedAssetHandler.workerPool(workers, maxQueued);
            this.budget = new GrapheneProxyBodyBudget(budgetBytes);
            this.deadline = deadline;
            this.deliveryDeadline = deliveryDeadline;
        }

        private GrapheneProxiedAssetHandler handler(GrapheneProxiedAssetHandler.Opener opener) {
            return new GrapheneProxiedAssetHandler(URL, opener, pool, TIMER, budget, deadline, deliveryDeadline);
        }
    }

    private static final class Probe implements Runnable {
        private final GrapheneProxiedAssetHandler drainOnReady;
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final AtomicReference<String> thread = new AtomicReference<>();
        private final AtomicInteger calls = new AtomicInteger();

        private Probe() {
            this(null);
        }

        private Probe(GrapheneProxiedAssetHandler drainOnReady) {
            this.drainOnReady = drainOnReady;
        }

        @Override
        public void run() {
            calls.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            if (drainOnReady != null) {
                drain(drainOnReady);
            }
            done.complete(null);
        }

        private void await() throws Exception {
            done.get(10, TimeUnit.SECONDS);
        }
    }

    /** A body that never arrives: reads block until the stream is closed, then fail. */
    private static final class BlockingStream extends InputStream {
        private final CountDownLatch reading = new CountDownLatch(1);
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private final Runnable onClose;
        private volatile boolean closed;

        private BlockingStream() {
            this(() -> {
            });
        }

        private BlockingStream(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public int read() throws IOException {
            reading.countDown();
            try {
                closedLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", interrupted);
            }
            throw new IOException("closed");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return read();
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                onClose.run();
                closedLatch.countDown();
            }
        }
    }

    private static final class CountingStream extends ByteArrayInputStream {
        private final AtomicInteger reads = new AtomicInteger();
        private volatile boolean closed;

        private CountingStream(byte[] content) {
            super(content);
        }

        @Override
        public synchronized int read() {
            reads.incrementAndGet();
            return super.read();
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            reads.incrementAndGet();
            return super.read(buffer, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
