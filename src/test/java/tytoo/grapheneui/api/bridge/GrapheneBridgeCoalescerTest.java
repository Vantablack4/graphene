package tytoo.grapheneui.api.bridge;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBridgeCoalescerTest {
    @Test
    void suppressesDuplicatePayloads() {
        TestBridge bridge = new TestBridge();
        AtomicLong now = new AtomicLong();
        GrapheneBridgeCoalescer coalescer = new GrapheneBridgeCoalescer(bridge, Duration.ZERO, now::get);

        assertTrue(coalescer.emit("surface:frame", "{\"value\":1}"));
        assertFalse(coalescer.emit("surface:frame", "{\"value\":1}"));

        assertEquals(List.of("surface:frame={\"value\":1}"), bridge.events);
    }

    @Test
    void keepsLatestPendingPayloadUntilIntervalPasses() {
        TestBridge bridge = new TestBridge();
        AtomicLong now = new AtomicLong();
        GrapheneBridgeCoalescer coalescer = new GrapheneBridgeCoalescer(bridge, Duration.ofMillis(50), now::get);

        assertTrue(coalescer.emit("surface:frame", "{\"value\":1}"));
        coalescer.queue("surface:frame", "{\"value\":2}");
        coalescer.queue("surface:frame", "{\"value\":3}");

        now.set(Duration.ofMillis(25).toNanos());
        assertFalse(coalescer.flush("surface:frame"));

        now.set(Duration.ofMillis(50).toNanos());
        assertTrue(coalescer.flush("surface:frame"));

        assertEquals(List.of("surface:frame={\"value\":1}", "surface:frame={\"value\":3}"), bridge.events);
    }

    @Test
    void waitsForReadyBridge() {
        TestBridge bridge = new TestBridge();
        bridge.ready = false;
        AtomicLong now = new AtomicLong();
        GrapheneBridgeCoalescer coalescer = new GrapheneBridgeCoalescer(bridge, Duration.ZERO, now::get);

        coalescer.queue("surface:frame", "{\"value\":1}");
        assertFalse(coalescer.flush());

        bridge.ready = true;
        assertEquals(1, coalescer.flush());
        assertEquals(List.of("surface:frame={\"value\":1}"), bridge.events);
    }

    private static final class TestBridge implements GrapheneBridge {
        private final List<String> events = new ArrayList<>();
        private boolean ready = true;

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public GrapheneBridgeSubscription onReady(Runnable listener) {
            return () -> {
            };
        }

        @Override
        public GrapheneBridgeSubscription onEvent(String channel, GrapheneBridgeEventListener listener) {
            return () -> {
            };
        }

        @Override
        public GrapheneBridgeSubscription onRequest(String channel, GrapheneBridgeRequestHandler handler) {
            return () -> {
            };
        }

        @Override
        public void emit(String channel, String payloadJson) {
            events.add(channel + "=" + payloadJson);
        }

        @Override
        public CompletableFuture<String> request(String channel, String payloadJson, Duration timeout) {
            return CompletableFuture.completedFuture("null");
        }
    }
}
