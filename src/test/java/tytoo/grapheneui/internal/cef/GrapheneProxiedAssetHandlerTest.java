package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneProxiedAssetHandlerTest {
    private static final String URL = "https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json";
    private static final Duration LONG = Duration.ofMinutes(1);

    @Test
    void onlyAnUpstreamOkIsPassedOnEverythingElseIsALocalError() {
        assertEquals(200, GrapheneProxiedAssetHandler.localStatusFor(200));
        assertEquals(404, GrapheneProxiedAssetHandler.localStatusFor(404));
        for (int upstream : new int[]{201, 204, 206, 301, 302, 304, 307, 401, 403, 410, 500, 503}) {
            assertEquals(502, GrapheneProxiedAssetHandler.localStatusFor(upstream), "upstream " + upstream);
        }
    }

    @Test
    void anAnsweredFetchIsServedAndItsContinuationRunsOnGraphenesOwnPool() throws Exception {
        byte[] body = {1, 2, 3};
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, body)), queue(1, 1), LONG);

        handler.start("GET", probe);

        probe.await();
        assertEquals(200, handler.status());
        assertArrayEquals(body, handler.body());
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

        GrapheneProxiedAssetHandler second = handler(url -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{2}));
        }, queue, LONG);
        Probe secondProbe = new Probe();
        second.start("GET", secondProbe);
        assertEquals(1, fetches.get(), "a queued request started before a slot was free");
        assertEquals(1, queue.waiting());

        GrapheneProxiedAssetHandler third = handler(url -> {
            fetches.incrementAndGet();
            return new CompletableFuture<>();
        }, queue, LONG);
        Probe thirdProbe = new Probe();
        third.start("GET", thirdProbe);
        thirdProbe.await();
        assertEquals(503, third.status());

        firstFetch.complete(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{1}));
        firstProbe.await();
        secondProbe.await();
        assertEquals(200, first.status());
        assertEquals(200, second.status());
        assertArrayEquals(new byte[]{2}, second.body());
        assertEquals(2, fetches.get());
        awaitIdle(queue);
    }

    @Test
    void aQueuedRequestCancelledByCefLeavesTheQueueAndNeverFetches() throws Exception {
        GrapheneProxyQueue queue = queue(1, 1);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> firstFetch = new CompletableFuture<>();
        GrapheneProxiedAssetHandler first = handler(url -> firstFetch, queue, LONG);
        first.start("GET", new Probe());

        AtomicInteger cancelledFetches = new AtomicInteger();
        GrapheneProxiedAssetHandler cancelled = handler(url -> {
            cancelledFetches.incrementAndGet();
            return new CompletableFuture<>();
        }, queue, LONG);
        Probe cancelledProbe = new Probe();
        cancelled.start("GET", cancelledProbe);
        cancelled.cancel();
        assertEquals(0, queue.waiting());

        GrapheneProxiedAssetHandler next = handler(
                url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{3})),
                queue,
                LONG
        );
        Probe nextProbe = new Probe();
        next.start("GET", nextProbe);
        assertEquals(1, queue.waiting(), "the cancelled request still held its place in the queue");

        firstFetch.complete(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{1}));
        nextProbe.await();
        assertEquals(200, next.status());
        assertEquals(0, cancelledFetches.get());
        assertEquals(0, cancelledProbe.calls.get());
        awaitIdle(queue);
    }

    @Test
    void aCefCancelOfARunningFetchEndsItAndHandsTheSlotOn() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> never = new CompletableFuture<>();
        GrapheneProxiedAssetHandler first = handler(url -> never, queue, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        GrapheneProxiedAssetHandler waiting = handler(
                url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{4})),
                queue,
                LONG
        );
        Probe waitingProbe = new Probe();
        waiting.start("GET", waitingProbe);

        first.cancel();

        assertTrue(never.isCancelled(), "a CEF cancel did not end the upstream fetch");
        waitingProbe.await();
        assertEquals(200, waiting.status());
        Thread.sleep(50);
        assertEquals(0, firstProbe.calls.get(), "a cancelled request was continued");
        awaitIdle(queue);
    }

    @Test
    void theDeadlineCountsFromWhenTheFetchStartsNotFromWhenItWasQueued() throws Exception {
        GrapheneProxyQueue queue = queue(1, 4);
        Duration deadline = Duration.ofMillis(400);
        GrapheneProxiedAssetHandler first = handler(url -> later(300, 1), queue, deadline);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);
        GrapheneProxiedAssetHandler queued = handler(url -> later(300, 2), queue, deadline);
        Probe queuedProbe = new Probe();
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
                Runnable::run
        );
        List<GrapheneProxiedAssetHandler> handlers = new ArrayList<>();
        List<Probe> probes = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            int tile = index;
            GrapheneProxiedAssetHandler handler = handler(url -> later(5 + tile % 7, tile), queue, LONG);
            Probe probe = new Probe();
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
        Probe failed = new Probe();
        GrapheneProxiedAssetHandler failing = handler(url -> CompletableFuture.failedFuture(new IllegalStateException("down")), queue(1, 1), LONG);
        failing.start("GET", failed);
        failed.await();
        assertEquals(502, failing.status());

        Probe oversized = new Probe();
        GrapheneProxiedAssetHandler tooLarge = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, null)), queue(1, 1), LONG);
        tooLarge.start("GET", oversized);
        oversized.await();
        assertEquals(502, tooLarge.status());

        Probe notFound = new Probe();
        GrapheneProxiedAssetHandler missing = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(404, null)), queue(1, 1), LONG);
        missing.start("GET", notFound);
        notFound.await();
        assertEquals(404, missing.status());

        AtomicInteger fetches = new AtomicInteger();
        Probe posted = new Probe();
        GrapheneProxiedAssetHandler post = handler(url -> {
            fetches.incrementAndGet();
            return new CompletableFuture<>();
        }, queue(1, 1), LONG);
        post.start("POST", posted);
        posted.await();
        assertEquals(405, post.status());
        assertEquals(0, fetches.get());
    }

    @Test
    void theBodyReaderStopsAtItsCap() {
        GrapheneProxiedAssetHandler.CappedBodySubscriber small = new GrapheneProxiedAssetHandler.CappedBodySubscriber(4);
        small.onSubscribe(new NoSubscription());
        small.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
        small.onComplete();
        assertArrayEquals(new byte[]{1, 2, 3}, small.getBody().toCompletableFuture().join());

        NoSubscription subscription = new NoSubscription();
        GrapheneProxiedAssetHandler.CappedBodySubscriber large = new GrapheneProxiedAssetHandler.CappedBodySubscriber(4);
        large.onSubscribe(subscription);
        large.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3}), ByteBuffer.wrap(new byte[]{4, 5})));
        assertNull(large.getBody().toCompletableFuture().join());
        assertTrue(subscription.cancelled);
    }

    private static GrapheneProxiedAssetHandler handler(
            Function<String, CompletableFuture<GrapheneProxiedAssetHandler.Fetched>> fetcher,
            GrapheneProxyQueue queue,
            Duration deadline
    ) {
        return new GrapheneProxiedAssetHandler(URL, fetcher, queue, deadline);
    }

    private static GrapheneProxyQueue queue(int maxRunning, int maxQueued) {
        return new GrapheneProxyQueue(maxRunning, maxQueued, Runnable::run);
    }

    private static CompletableFuture<GrapheneProxiedAssetHandler.Fetched> later(long millis, int marker) {
        return CompletableFuture.supplyAsync(
                () -> new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{(byte) marker}),
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
        );
    }

    private static void awaitIdle(GrapheneProxyQueue queue) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && (queue.running() > 0 || queue.waiting() > 0); attempt++) {
            Thread.sleep(10);
        }

        assertEquals(0, queue.running(), "a slot was never given back");
        assertEquals(0, queue.waiting());
    }

    private static final class Probe implements Runnable {
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final AtomicReference<String> thread = new AtomicReference<>();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void run() {
            calls.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            done.complete(null);
        }

        private void await() throws Exception {
            done.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class NoSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long count) {
            // The test pushes the buffers itself.
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
