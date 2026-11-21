package tytoo.grapheneui.internal.cef;

import org.cef.misc.IntRef;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneProxiedAssetHandlerTest {
    private static final String URL = "https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json";
    private static final Duration LONG = Duration.ofMinutes(1);
    private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, GrapheneProxiedAssetHandler.THREAD_NAME + "-test");
        thread.setDaemon(true);
        return thread;
    });

    @Test
    void onlyAnUpstreamOkIsPassedOnEverythingElseIsALocalError() {
        assertEquals(200, GrapheneProxiedAssetHandler.localStatusFor(200));
        assertEquals(404, GrapheneProxiedAssetHandler.localStatusFor(404));
        for (int upstream : new int[]{201, 204, 206, 301, 302, 304, 307, 401, 403, 410, 500, 503}) {
            assertEquals(502, GrapheneProxiedAssetHandler.localStatusFor(upstream), "upstream " + upstream);
        }
    }

    @Test
    void theFetchStartsAndContinuesOnGraphenesOwnPoolNeverOnTheCallersThread() throws Exception {
        byte[] body = {1, 2, 3};
        AtomicReference<String> fetchThread = new AtomicReference<>();
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = handler(url -> {
            fetchThread.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, body));
        }, queue(1, 1), LONG);

        handler.start("GET", probe);

        probe.await();
        assertEquals(200, handler.status());
        assertArrayEquals(body, handler.body());
        assertTrue(fetchThread.get().startsWith(GrapheneProxiedAssetHandler.THREAD_NAME), fetchThread.get());
        assertTrue(probe.thread.get().startsWith(GrapheneProxiedAssetHandler.THREAD_NAME), probe.thread.get());
    }

    @Test
    void aBodyThatDoesNotArriveBeforeTheDeadlineIsALocalTimeoutAndFreesItsSlot() throws Exception {
        GrapheneProxyQueue queue = queue(1, 1);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> never = new CompletableFuture<>();
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = handler(url -> never, queue, Duration.ofMillis(100));

        handler.start("GET", probe);

        probe.await();
        assertEquals(504, handler.status());
        assertEquals(0, handler.body().length);
        assertTrue(never.isDone(), "the upstream future was not ended at the deadline");
        awaitIdle(queue);
    }

    @Test
    void aSlotIsHeldUntilCefHasReadTheBody() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        AtomicInteger fetches = new AtomicInteger();
        GrapheneProxiedAssetHandler first = handler(url -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{1, 2, 3, 4, 5}));
        }, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        firstProbe.await();

        GrapheneProxiedAssetHandler second = handler(url -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{6}));
        }, queue, LONG);
        Probe secondProbe = new Probe();
        second.start("GET", secondProbe);
        Thread.sleep(100);
        assertEquals(1, fetches.get(), "the next fetch started while the first body was still buffered");
        assertEquals(1, queue.running());

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, drain(first));
        secondProbe.await();
        assertEquals(2, fetches.get());
        assertEquals(0, first.body().length, "the delivered body stayed reachable");
        drain(second);
        awaitIdle(queue);
    }

    @Test
    void requestsBeyondTheRunningLimitWaitAndOnlyAFullQueueGets503() throws Exception {
        GrapheneProxyQueue queue = queue(1, 1);
        AtomicInteger fetches = new AtomicInteger();
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> firstFetch = new CompletableFuture<>();
        GrapheneProxiedAssetHandler first = handler(url -> {
            fetches.incrementAndGet();
            return firstFetch;
        }, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        eventually(() -> fetches.get() == 1);

        GrapheneProxiedAssetHandler second = handler(url -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{2}));
        }, queue, LONG);
        Probe secondProbe = new Probe();
        second.start("GET", secondProbe);
        assertEquals(1, queue.waiting());

        GrapheneProxiedAssetHandler third = handler(url -> new CompletableFuture<>(), queue, LONG);
        Probe thirdProbe = new Probe();
        third.start("GET", thirdProbe);
        thirdProbe.await();
        assertEquals(503, third.status());

        firstFetch.complete(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{1}));
        firstProbe.await();
        drain(first);
        secondProbe.await();
        assertEquals(200, second.status());
        assertArrayEquals(new byte[]{2}, drain(second));
        assertEquals(2, fetches.get());
        awaitIdle(queue);
    }

    @Test
    void aQueuedRequestCancelledByCefLeavesTheQueueAndNeverFetches() throws Exception {
        GrapheneProxyQueue queue = queue(1, 1);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> firstFetch = new CompletableFuture<>();
        GrapheneProxiedAssetHandler first = handler(url -> firstFetch, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);

        AtomicInteger cancelledFetches = new AtomicInteger();
        GrapheneProxiedAssetHandler cancelled = handler(url -> {
            cancelledFetches.incrementAndGet();
            return new CompletableFuture<>();
        }, queue, LONG);
        Probe cancelledProbe = new Probe();
        cancelled.start("GET", cancelledProbe);
        cancelled.cancel();
        eventually(() -> queue.waiting() == 0);

        GrapheneProxiedAssetHandler next = handler(
                url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{3})),
                queue,
                LONG
        );
        Probe nextProbe = new Probe();
        next.start("GET", nextProbe);
        assertEquals(1, queue.waiting(), "the cancelled request still held its place in the queue");

        firstFetch.complete(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{1}));
        firstProbe.await();
        drain(first);
        nextProbe.await();
        assertEquals(200, next.status());
        drain(next);
        assertEquals(0, cancelledFetches.get());
        assertEquals(0, cancelledProbe.calls.get());
        awaitIdle(queue);
    }

    @Test
    void aCefCancelOfARunningFetchEndsItOnGraphenesPoolAndHandsTheSlotOn() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> never = new CompletableFuture<>();
        AtomicReference<String> cancelThread = new AtomicReference<>();
        never.whenComplete((ignoredResult, ignoredFailure) -> cancelThread.set(Thread.currentThread().getName()));
        AtomicInteger fetches = new AtomicInteger();
        GrapheneProxiedAssetHandler first = handler(url -> {
            fetches.incrementAndGet();
            return never;
        }, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        eventually(() -> fetches.get() == 1);
        GrapheneProxiedAssetHandler waiting = handler(
                url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{4})),
                queue,
                LONG
        );
        Probe waitingProbe = new Probe();
        waiting.start("GET", waitingProbe);

        first.cancel();

        waitingProbe.await();
        assertTrue(never.isCancelled(), "a CEF cancel did not end the upstream fetch");
        assertTrue(cancelThread.get().startsWith(GrapheneProxiedAssetHandler.THREAD_NAME), cancelThread.get());
        assertEquals(200, waiting.status());
        drain(waiting);
        Thread.sleep(50);
        assertEquals(0, firstProbe.calls.get(), "a cancelled request was continued");
        awaitIdle(queue);
    }

    @Test
    void aCancelDuringStartupIsHonouredOnceTheExchangeExistsAndKeepsTheSlotUntilThen() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        CountDownLatch insideFetch = new CountDownLatch(1);
        CountDownLatch letFetchReturn = new CountDownLatch(1);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> exchange = new CompletableFuture<>();
        List<String> order = new ArrayList<>();
        exchange.whenComplete((ignoredResult, ignoredFailure) -> record(order, "first exchange ended"));
        GrapheneProxiedAssetHandler first = handler(url -> {
            insideFetch.countDown();
            await(letFetchReturn);
            return exchange;
        }, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        assertTrue(insideFetch.await(5, TimeUnit.SECONDS));

        GrapheneProxiedAssetHandler second = handler(url -> {
            record(order, "second fetch started");
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{5}));
        }, queue, LONG);
        Probe secondProbe = new Probe();
        second.start("GET", secondProbe);

        first.cancel();
        Thread.sleep(100);
        assertEquals(1, queue.running(), "the slot was handed on while the first exchange was still starting");
        assertEquals(1, queue.waiting());

        letFetchReturn.countDown();
        secondProbe.await();
        assertTrue(exchange.isCancelled(), "the exchange published after the cancel was left running");
        assertEquals(List.of("first exchange ended", "second fetch started"), snapshot(order));
        drain(second);
        assertEquals(0, firstProbe.calls.get());
        awaitIdle(queue);
    }

    @Test
    void theDeadlineCountsFromWhenTheFetchStartsNotFromWhenItWasQueued() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        Duration deadline = Duration.ofMillis(400);
        GrapheneProxiedAssetHandler first = handler(url -> later(300, 1), queue, deadline);
        Probe firstProbe = new Probe(first);
        first.start("GET", firstProbe);
        GrapheneProxiedAssetHandler queued = handler(url -> later(300, 2), queue, deadline);
        Probe queuedProbe = new Probe(queued);
        long queuedAt = System.nanoTime();
        queued.start("GET", queuedProbe);

        queuedProbe.await();
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
        assertEquals(200, first.status());
        assertEquals(200, queued.status(), "a queued request timed out while it was waiting");
        assertTrue(waitedMillis > deadline.toMillis(), "the test did not wait past one deadline: " + waitedMillis);
        awaitIdle(queue);
    }

    @Test
    void aThumbnailGridNeverGets503() throws Exception {
        GrapheneProxyQueue queue = new GrapheneProxyQueue(
                GrapheneProxiedAssetHandler.MAX_RUNNING,
                GrapheneProxiedAssetHandler.MAX_QUEUED,
                POOL
        );
        List<GrapheneProxiedAssetHandler> handlers = new ArrayList<>();
        List<Probe> probes = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            int tile = index;
            GrapheneProxiedAssetHandler handler = handler(url -> later(5 + tile % 7, tile), queue, LONG);
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
        assertEquals(GrapheneProxiedAssetHandler.MAX_RUNNING, queue.peakRunning());
        assertTrue(queue.peakWaiting() > 0, "the grid never queued");
        awaitIdle(queue);
    }

    @Test
    void failuresAndOversizedBodiesAreLocalErrorsAndOtherMethodsAreRefused() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        Probe failed = new Probe();
        GrapheneProxiedAssetHandler failing = handler(url -> CompletableFuture.failedFuture(new IllegalStateException("down")), queue, LONG);
        failing.start("GET", failed);
        failed.await();
        assertEquals(502, failing.status());

        Probe oversized = new Probe();
        GrapheneProxiedAssetHandler tooLarge = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, null)), queue, LONG);
        tooLarge.start("GET", oversized);
        oversized.await();
        assertEquals(502, tooLarge.status());

        Probe notFound = new Probe();
        GrapheneProxiedAssetHandler missing = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(404, null)), queue, LONG);
        missing.start("GET", notFound);
        notFound.await();
        assertEquals(404, missing.status());
        awaitIdle(queue);

        AtomicInteger fetches = new AtomicInteger();
        Probe posted = new Probe();
        GrapheneProxiedAssetHandler post = handler(url -> {
            fetches.incrementAndGet();
            return new CompletableFuture<>();
        }, queue, LONG);
        post.start("POST", posted);
        posted.await();
        assertEquals(405, post.status());
        assertEquals(0, fetches.get());
    }

    @Test
    void theBodyReaderStopsAtItsCapOfFourMebibytes() {
        assertEquals(4 * 1024 * 1024, GrapheneProxiedAssetHandler.MAX_BODY_BYTES);

        GrapheneProxiedAssetHandler.CappedBodySubscriber small = new GrapheneProxiedAssetHandler.CappedBodySubscriber(4);
        small.onSubscribe(new RecordingSubscription());
        small.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
        small.onComplete();
        assertArrayEquals(new byte[]{1, 2, 3}, small.getBody().toCompletableFuture().join());

        RecordingSubscription subscription = new RecordingSubscription();
        GrapheneProxiedAssetHandler.CappedBodySubscriber large = new GrapheneProxiedAssetHandler.CappedBodySubscriber(4);
        large.onSubscribe(subscription);
        large.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3}), ByteBuffer.wrap(new byte[]{4, 5})));
        assertNull(large.getBody().toCompletableFuture().join());
        assertTrue(subscription.cancelled);
    }

    @Test
    void aBodyDeclaredTooLargeOrNotOkIsStoppedAtTheHeadersUnread() {
        for (ResponseInfo info : List.of(
                new ResponseInfo(200, GrapheneProxiedAssetHandler.MAX_BODY_BYTES + 1L),
                new ResponseInfo(500, 10L),
                new ResponseInfo(404, -1L)
        )) {
            RecordingSubscription subscription = new RecordingSubscription();
            HttpResponse.BodySubscriber<byte[]> subscriber = GrapheneProxiedAssetHandler.cappedBody(info);
            subscriber.onSubscribe(subscription);
            assertTrue(subscription.cancelled, info + " was not stopped at the headers");
            assertEquals(0L, subscription.requested, info + " asked for its body");
            assertNull(subscriber.getBody().toCompletableFuture().join());
        }

        RecordingSubscription normal = new RecordingSubscription();
        GrapheneProxiedAssetHandler.cappedBody(new ResponseInfo(200, 1000L)).onSubscribe(normal);
        assertFalse(normal.cancelled);
        assertTrue(normal.requested > 0);
    }

    private static GrapheneProxiedAssetHandler handler(
            Function<String, CompletableFuture<GrapheneProxiedAssetHandler.Fetched>> fetcher,
            GrapheneProxyQueue queue,
            Duration deadline
    ) {
        return new GrapheneProxiedAssetHandler(URL, fetcher, queue, POOL, deadline);
    }

    private static GrapheneProxyQueue queue(int maxRunning, int maxQueued) {
        return new GrapheneProxyQueue(maxRunning, maxQueued, POOL);
    }

    private static CompletableFuture<GrapheneProxiedAssetHandler.Fetched> later(long millis, int marker) {
        return CompletableFuture.supplyAsync(
                () -> new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{(byte) marker}),
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
        );
    }

    private static byte[] drain(GrapheneProxiedAssetHandler handler) {
        List<Byte> bytes = new ArrayList<>();
        byte[] buffer = new byte[2];
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 500 && !condition.getAsBoolean(); attempt++) {
            Thread.sleep(10);
        }

        assertTrue(condition.getAsBoolean(), "a condition never became true");
    }

    private static void awaitIdle(GrapheneProxyQueue queue) throws InterruptedException {
        for (int attempt = 0; attempt < 500 && (queue.running() > 0 || queue.waiting() > 0); attempt++) {
            Thread.sleep(10);
        }

        assertEquals(0, queue.running(), "a slot was never given back");
        assertEquals(0, queue.waiting());
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

    private static final class RecordingSubscription implements Flow.Subscription {
        private boolean cancelled;
        private long requested;

        @Override
        public void request(long count) {
            requested += count;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private record ResponseInfo(int statusCode, long contentLength) implements HttpResponse.ResponseInfo {
        @Override
        public HttpHeaders headers() {
            Map<String, List<String>> headers = contentLength < 0
                    ? Map.of()
                    : Map.of("content-length", List.of(Long.toString(contentLength)));
            return HttpHeaders.of(headers, (ignoredName, ignoredValue) -> true);
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
