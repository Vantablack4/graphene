package tytoo.grapheneui.internal.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class GrapheneBridgeOutboundQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneBridgeOutboundQueue.class);
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneBridgeOutboundQueue.class);

    private final Object lock = new Object();
    private final ArrayDeque<String> queuedMessages = new ArrayDeque<>();
    private final LinkedHashMap<String, String> latestQueuedMessages = new LinkedHashMap<>();
    private final Consumer<String> dispatcher;
    private final int maxQueuedMessages;
    private final GrapheneBridgeQueueOverflowPolicy overflowPolicy;
    private final GrapheneBridgeDiagnostics diagnostics;
    private State state = State.NOT_READY;

    GrapheneBridgeOutboundQueue(
            Consumer<String> dispatcher,
            int maxQueuedMessages,
            GrapheneBridgeQueueOverflowPolicy overflowPolicy,
            GrapheneBridgeDiagnostics diagnostics
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        if (maxQueuedMessages < 1) {
            throw new IllegalArgumentException("maxQueuedMessages must be >= 1");
        }
        this.maxQueuedMessages = maxQueuedMessages;
        this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    boolean isReady() {
        synchronized (lock) {
            return state == State.READY;
        }
    }

    void markNotReady() {
        synchronized (lock) {
            state = State.NOT_READY;
            DEBUG_LOGGER.debug("Bridge outbound queue marked NOT_READY queued={}", queuedMessageCountLocked());
        }
    }

    void markReadyAndFlush() {
        while (true) {
            List<String> messagesToDispatch;
            synchronized (lock) {
                if (state == State.READY) {
                    return;
                }

                state = State.FLUSHING;
                if (queuedMessageCountLocked() == 0) {
                    state = State.READY;
                    return;
                }

                messagesToDispatch = drainQueuedMessagesLocked();
            }

            for (String message : messagesToDispatch) {
                try {
                    dispatcher.accept(message);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to dispatch queued Graphene bridge message", exception);
                }
            }

            DEBUG_LOGGER.debug("Flushed {} queued bridge outbound message(s)", messagesToDispatch.size());
        }
    }

    void queueOrDispatch(String outboundPacketJson) {
        Objects.requireNonNull(outboundPacketJson, "outboundPacketJson");

        synchronized (lock) {
            if (state != State.READY) {
                queueMessageLocked(outboundPacketJson);
                DEBUG_LOGGER.debug("Queued bridge outbound message size={} queued={}", outboundPacketJson.length(), queuedMessageCountLocked());
                return;
            }

            dispatchImmediateLocked(outboundPacketJson);
        }
    }

    void queueLatestOrDispatch(String coalescingKey, String outboundPacketJson) {
        Objects.requireNonNull(coalescingKey, "coalescingKey");
        Objects.requireNonNull(outboundPacketJson, "outboundPacketJson");

        synchronized (lock) {
            if (state != State.READY) {
                queueLatestMessageLocked(coalescingKey, outboundPacketJson);
                DEBUG_LOGGER.debug(
                        "Queued latest bridge outbound message key={} size={} queued={}",
                        coalescingKey,
                        outboundPacketJson.length(),
                        queuedMessageCountLocked()
                );
                return;
            }

            dispatchImmediateLocked(outboundPacketJson);
        }
    }

    void clear() {
        synchronized (lock) {
            queuedMessages.clear();
            latestQueuedMessages.clear();
        }
    }

    private void queueMessageLocked(String outboundPacketJson) {
        if (queuedMessageCountLocked() < maxQueuedMessages) {
            queuedMessages.addLast(outboundPacketJson);
            return;
        }

        handleOverflowLocked(outboundPacketJson, () -> queuedMessages.addLast(outboundPacketJson));
    }

    private void queueLatestMessageLocked(String coalescingKey, String outboundPacketJson) {
        if (latestQueuedMessages.containsKey(coalescingKey)) {
            latestQueuedMessages.put(coalescingKey, outboundPacketJson);
            return;
        }

        if (queuedMessageCountLocked() < maxQueuedMessages) {
            latestQueuedMessages.put(coalescingKey, outboundPacketJson);
            return;
        }

        handleOverflowLocked(outboundPacketJson, () -> latestQueuedMessages.put(coalescingKey, outboundPacketJson));
    }

    private void handleOverflowLocked(String outboundPacketJson, Runnable enqueueAfterDrop) {
        if (overflowPolicy == GrapheneBridgeQueueOverflowPolicy.DROP_NEWEST) {
            diagnostics.onOutboundMessageDropped(outboundPacketJson, overflowPolicy, maxQueuedMessages);
            DEBUG_LOGGER.debug(
                    "Dropped newest bridge outbound message size={} policy={} maxQueued={}",
                    outboundPacketJson.length(),
                    overflowPolicy,
                    maxQueuedMessages
            );
            return;
        }

        if (overflowPolicy == GrapheneBridgeQueueOverflowPolicy.DROP_OLDEST) {
            String droppedMessage = removeOldestMessageLocked();
            enqueueAfterDrop.run();
            diagnostics.onOutboundMessageDropped(droppedMessage, overflowPolicy, maxQueuedMessages);
            DEBUG_LOGGER.debug(
                    "Dropped oldest bridge outbound message droppedSize={} newSize={} maxQueued={}",
                    droppedMessage.length(),
                    outboundPacketJson.length(),
                    maxQueuedMessages
            );
            return;
        }

        throw new IllegalStateException("Bridge outbound queue reached max size " + maxQueuedMessages);
    }

    private void dispatchImmediateLocked(String outboundPacketJson) {
        try {
            dispatcher.accept(outboundPacketJson);
            DEBUG_LOGGER.debug("Dispatched bridge outbound message immediately size={}", outboundPacketJson.length());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to dispatch immediate Graphene bridge message", exception);
        }
    }

    private int queuedMessageCountLocked() {
        return queuedMessages.size() + latestQueuedMessages.size();
    }

    private String removeOldestMessageLocked() {
        if (!queuedMessages.isEmpty()) {
            return queuedMessages.removeFirst();
        }

        Iterator<Map.Entry<String, String>> iterator = latestQueuedMessages.entrySet().iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException("Bridge outbound queue has no message to drop");
        }

        Map.Entry<String, String> entry = iterator.next();
        String droppedMessage = entry.getValue();
        iterator.remove();
        return droppedMessage;
    }

    private List<String> drainQueuedMessagesLocked() {
        List<String> messages = new ArrayList<>(queuedMessageCountLocked());
        while (!queuedMessages.isEmpty()) {
            messages.add(queuedMessages.removeFirst());
        }
        messages.addAll(latestQueuedMessages.values());
        latestQueuedMessages.clear();

        return messages;
    }

    private enum State {
        NOT_READY,
        FLUSHING,
        READY
    }
}
