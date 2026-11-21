package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
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
        GrapheneProxiedAssetHandler handler = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, body)), new Semaphore(1), LONG);

        handler.start("GET", probe);

        probe.await();
        assertEquals(200, handler.status());
        assertArrayEquals(body, handler.body());
        assertTrue(probe.thread.get().startsWith(GrapheneProxiedAssetHandler.THREAD_NAME), probe.thread.get());
    }

    @Test
    void aBodyThatDoesNotArriveBeforeTheDeadlineIsALocalTimeoutAndFreesItsSlot() throws Exception {
        Semaphore slots = new Semaphore(1);
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> never = new CompletableFuture<>();
        Probe probe = new Probe();
        GrapheneProxiedAssetHandler handler = handler(url -> never, slots, Duration.ofMillis(100));

        handler.start("GET", probe);

        probe.await();
        assertEquals(504, handler.status());
        assertEquals(0, handler.body().length);
        assertTrue(never.isDone(), "the upstream future was not ended at the deadline");
        assertEquals(1, slots.availablePermits());
    }

    @Test
    void requestsBeyondTheLimitGetALocal503AndACancelFreesTheSlot() throws Exception {
        Semaphore slots = new Semaphore(1);
        AtomicInteger fetches = new AtomicInteger();
        CompletableFuture<GrapheneProxiedAssetHandler.Fetched> never = new CompletableFuture<>();
        GrapheneProxiedAssetHandler first = handler(url -> {
            fetches.incrementAndGet();
            return never;
        }, slots, LONG);
        Probe firstProbe = new Probe();
        first.start("GET", firstProbe);

        Probe secondProbe = new Probe();
        GrapheneProxiedAssetHandler second = handler(url -> {
            fetches.incrementAndGet();
            return new CompletableFuture<>();
        }, slots, LONG);
        second.start("GET", secondProbe);
        secondProbe.await();
        assertEquals(503, second.status());
        assertEquals(1, fetches.get());

        first.cancel();
        assertTrue(never.isCancelled(), "a CEF cancel did not end the upstream fetch");
        assertEquals(1, slots.availablePermits());
        Thread.sleep(50);
        assertEquals(0, firstProbe.calls.get(), "a cancelled request was continued");

        Probe thirdProbe = new Probe();
        GrapheneProxiedAssetHandler third = handler(url -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, new byte[]{9}));
        }, slots, LONG);
        third.start("GET", thirdProbe);
        thirdProbe.await();
        assertEquals(200, third.status());
        assertEquals(2, fetches.get());
        assertEquals(1, slots.availablePermits());
    }

    @Test
    void failuresAndOversizedBodiesAreLocalErrorsAndOtherMethodsAreRefused() throws Exception {
        Probe failed = new Probe();
        GrapheneProxiedAssetHandler failing = handler(url -> CompletableFuture.failedFuture(new IllegalStateException("down")), new Semaphore(1), LONG);
        failing.start("GET", failed);
        failed.await();
        assertEquals(502, failing.status());

        Probe oversized = new Probe();
        GrapheneProxiedAssetHandler tooLarge = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(200, null)), new Semaphore(1), LONG);
        tooLarge.start("GET", oversized);
        oversized.await();
        assertEquals(502, tooLarge.status());

        Probe notFound = new Probe();
        GrapheneProxiedAssetHandler missing = handler(url -> CompletableFuture.completedFuture(new GrapheneProxiedAssetHandler.Fetched(404, null)), new Semaphore(1), LONG);
        missing.start("GET", notFound);
        notFound.await();
        assertEquals(404, missing.status());

        AtomicInteger fetches = new AtomicInteger();
        Probe posted = new Probe();
        GrapheneProxiedAssetHandler post = handler(url -> {
            fetches.incrementAndGet();
            return new CompletableFuture<>();
        }, new Semaphore(1), LONG);
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
            Semaphore slots,
            Duration deadline
    ) {
        return new GrapheneProxiedAssetHandler(URL, fetcher, slots, deadline);
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
