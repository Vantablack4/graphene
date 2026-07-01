package tytoo.grapheneui.api.bridge;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Coalesces high-frequency Java-to-page bridge events by channel.
 * <p>
 * Use one coalescer per bridge when a surface emits render or simulation state every tick. The coalescer keeps only the
 * latest pending payload for each channel, suppresses duplicate payloads that have already been sent, and enforces a
 * minimum interval between sends.
 */
@SuppressWarnings("unused")
public final class GrapheneBridgeCoalescer {
    private final GrapheneBridge bridge;
    private final long minIntervalNanos;
    private final LongSupplier nanoTimeSource;
    private final Map<String, ChannelState> channels = new HashMap<>();

    public GrapheneBridgeCoalescer(GrapheneBridge bridge, Duration minInterval) {
        this(bridge, minInterval, System::nanoTime);
    }

    GrapheneBridgeCoalescer(GrapheneBridge bridge, Duration minInterval, LongSupplier nanoTimeSource) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.minIntervalNanos = requireNonNegative(Objects.requireNonNull(minInterval, "minInterval"));
        this.nanoTimeSource = Objects.requireNonNull(nanoTimeSource, "nanoTimeSource");
    }

    public static GrapheneBridgeCoalescer create(GrapheneBridge bridge, Duration minInterval) {
        return new GrapheneBridgeCoalescer(bridge, minInterval);
    }

    public boolean emit(String channel, String payloadJson) {
        queue(channel, payloadJson);
        return flush(channel);
    }

    public boolean emitJson(String channel, Object payload) {
        return emit(channel, GrapheneBridgeJson.toJson(payload));
    }

    public void queue(String channel, String payloadJson) {
        String validatedChannel = validateChannel(channel);
        String normalizedPayload = normalizePayload(payloadJson);
        ChannelState state = channels.computeIfAbsent(validatedChannel, ignored -> new ChannelState());
        if (Objects.equals(state.lastSentPayloadJson, normalizedPayload)
                && state.pendingPayloadJson == null) {
            return;
        }

        state.pendingPayloadJson = normalizedPayload;
    }

    public void queueJson(String channel, Object payload) {
        queue(channel, GrapheneBridgeJson.toJson(payload));
    }

    public boolean flush(String channel) {
        String validatedChannel = validateChannel(channel);
        ChannelState state = channels.get(validatedChannel);
        if (state == null || state.pendingPayloadJson == null || !bridge.isReady()) {
            return false;
        }

        long now = nanoTimeSource.getAsLong();
        if (!state.canSend(now, minIntervalNanos)) {
            return false;
        }

        String payloadJson = state.pendingPayloadJson;
        if (Objects.equals(state.lastSentPayloadJson, payloadJson)) {
            state.pendingPayloadJson = null;
            return false;
        }

        bridge.emit(validatedChannel, payloadJson);
        state.lastSentPayloadJson = payloadJson;
        state.pendingPayloadJson = null;
        state.lastSentNanos = now;
        return true;
    }

    public int flush() {
        int sent = 0;
        for (String channel : channels.keySet().toArray(String[]::new)) {
            if (flush(channel)) {
                sent++;
            }
        }
        return sent;
    }

    public void clear() {
        channels.clear();
    }

    public void clear(String channel) {
        channels.remove(validateChannel(channel));
    }

    private static String validateChannel(String channel) {
        String validatedChannel = Objects.requireNonNull(channel, "channel").trim();
        if (validatedChannel.isEmpty()) {
            throw new IllegalArgumentException("channel must not be blank");
        }

        return validatedChannel;
    }

    private static String normalizePayload(String payloadJson) {
        return payloadJson == null ? "null" : payloadJson;
    }

    private static long requireNonNegative(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("minInterval must not be negative");
        }

        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            // Clamp extreme intervals rather than failing construction.
            return Long.MAX_VALUE;
        }
    }

    private static final class ChannelState {
        private String pendingPayloadJson;
        private String lastSentPayloadJson;
        private long lastSentNanos = Long.MIN_VALUE;

        private boolean canSend(long now, long minIntervalNanos) {
            return lastSentNanos == Long.MIN_VALUE || now - lastSentNanos >= minIntervalNanos;
        }
    }
}
