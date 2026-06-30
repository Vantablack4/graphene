package tytoo.grapheneui.internal.nativeui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.profiling.ProfilerFiller;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.bridge.GrapheneBridgeJson;
import tytoo.grapheneui.api.bridge.GrapheneBridgeSubscription;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.internal.logging.GrapheneDebugLogger;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GrapheneNativeSlotRegistry implements GrapheneNativeSlots {
    private static final int MAX_RETIRED_PAGE_IDS = 32;
    private static final GrapheneDebugLogger DEBUG_LOGGER = GrapheneDebugLogger.of(GrapheneNativeSlotRegistry.class);

    private final Object lock = new Object();
    private final GrapheneVanillaNativeSlotRenderers renderers = new GrapheneVanillaNativeSlotRenderers();
    private final Map<String, GrapheneNativeSlot> slotsById = new LinkedHashMap<>();
    private final Map<String, Long> lastSequenceByPageId = new HashMap<>();
    private final Set<String> retiredPageIds = new HashSet<>();
    private final Deque<String> retiredPageIdOrder = new ArrayDeque<>();
    private final GrapheneBridgeSubscription frameSubscription;
    private final GrapheneBridgeSubscription resetSubscription;
    private GrapheneNativeSlotPointer pointer = GrapheneNativeSlotPointer.unavailable();
    private GrapheneNativeSlotViewport activeViewport;
    private String activePageId;
    private boolean closed;

    public GrapheneNativeSlotRegistry(GrapheneBridge bridge) {
        GrapheneBridge validatedBridge = Objects.requireNonNull(bridge, "bridge");
        this.frameSubscription = validatedBridge.onEvent(GrapheneNativeSlotChannels.FRAME, this::handleFrameEvent);
        this.resetSubscription = validatedBridge.onEvent(GrapheneNativeSlotChannels.RESET, this::handleResetEvent);
    }

    @Override
    public void clear() {
        synchronized (lock) {
            slotsById.clear();
            activePageId = null;
            activeViewport = null;
            lastSequenceByPageId.clear();
            retiredPageIds.clear();
            retiredPageIdOrder.clear();
        }
    }

    @Override
    public void clearPageSlots() {
        synchronized (lock) {
            if (closed) {
                return;
            }

            retireActivePageIdLocked();
            slotsById.clear();
            activePageId = null;
            activeViewport = null;
        }
    }

    @Override
    public int size() {
        synchronized (lock) {
            return slotsById.size();
        }
    }

    @Override
    public void setPointer(int mouseX, int mouseY) {
        synchronized (lock) {
            pointer = GrapheneNativeSlotPointer.at(mouseX, mouseY);
        }
    }

    @Override
    public void clearPointer() {
        synchronized (lock) {
            pointer = GrapheneNativeSlotPointer.unavailable();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }

            closed = true;
            slotsById.clear();
            activePageId = null;
            activeViewport = null;
            lastSequenceByPageId.clear();
            retiredPageIds.clear();
            retiredPageIdOrder.clear();
        }

        frameSubscription.unsubscribe();
        resetSubscription.unsubscribe();
    }

    public void render(
            ProfilerFiller profiler,
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int resolutionWidth,
            int resolutionHeight,
            int viewBoxX,
            int viewBoxY,
            int viewBoxWidth,
            int viewBoxHeight
    ) {
        List<GrapheneNativeSlot> slots = snapshot();
        if (slots.isEmpty()) {
            return;
        }

        GrapheneNativeSlotViewport viewport = viewport(resolutionWidth, resolutionHeight);
        GrapheneNativeSlotRenderTarget target = new GrapheneNativeSlotRenderTarget(
                x,
                y,
                width,
                height,
                resolutionWidth,
                resolutionHeight,
                viewBoxX,
                viewBoxY,
                viewBoxWidth,
                viewBoxHeight
        );

        profiler.push("native_slots");
        try {
            graphics.nextStratum();
            for (GrapheneNativeSlot slot : slots) {
                GrapheneNativeSlotRenderer renderer = renderers.renderer(slot.kind());
                if (renderer == null || !slot.shouldRender()) {
                    continue;
                }

                GrapheneNativeSlotScreenRect bounds = GrapheneNativeSlotBoundsMapper.map(slot.rect(), viewport, target);
                if (bounds == null) {
                    continue;
                }

                try {
                    renderer.render(new GrapheneNativeSlotRenderContext(graphics, slot, bounds, pointer()));
                } catch (RuntimeException exception) {
                    DEBUG_LOGGER.debug("Native slot renderer failed slotId={} kind={}", slot.id(), slot.kind(), exception);
                }
            }
        } finally {
            profiler.pop();
        }
    }

    private void handleFrameEvent(String ignoredChannel, String payloadJson) {
        GrapheneNativeSlotFramePayload frame;
        try {
            frame = GrapheneBridgeJson.fromJson(payloadJson, GrapheneNativeSlotFramePayload.class);
        } catch (RuntimeException exception) {
            DEBUG_LOGGER.debug("Failed to parse native slot frame", exception);
            return;
        }

        McClient.runOnMainThread(() -> applyFrame(frame));
    }

    private void handleResetEvent(String ignoredChannel, String payloadJson) {
        GrapheneNativeSlotFramePayload reset;
        try {
            reset = GrapheneBridgeJson.fromJson(payloadJson, GrapheneNativeSlotFramePayload.class);
        } catch (RuntimeException exception) {
            DEBUG_LOGGER.debug("Failed to parse native slot reset", exception);
            return;
        }

        McClient.runOnMainThread(() -> applyReset(reset));
    }

    private void applyFrame(GrapheneNativeSlotFramePayload frame) {
        if (!isValidFrame(frame)) {
            return;
        }

        synchronized (lock) {
            if (closed || retiredPageIds.contains(frame.pageId) || !acceptSequenceLocked(frame.pageId, frame.seq)) {
                return;
            }

            if (activePageId != null && !activePageId.equals(frame.pageId)) {
                retireActivePageIdLocked();
                slotsById.clear();
            }
            activePageId = frame.pageId;
            activeViewport = frame.viewport == null ? null : GrapheneNativeSlotViewport.fromPayload(frame.viewport, 1, 1);

            if (frame.removed != null) {
                for (String removedId : frame.removed) {
                    if (removedId != null) {
                        slotsById.remove(removedId);
                    }
                }
            }

            if (frame.slots != null) {
                for (GrapheneNativeSlotPayload slotPayload : frame.slots) {
                    GrapheneNativeSlot slot = GrapheneNativeSlot.fromPayload(slotPayload);
                    if (slot != null) {
                        slotsById.put(slot.id(), slot);
                    }
                }
            }
        }
    }

    private void applyReset(GrapheneNativeSlotFramePayload reset) {
        if (reset == null || reset.version != GrapheneNativeSlotChannels.VERSION) {
            return;
        }

        synchronized (lock) {
            if (closed) {
                return;
            }

            if (reset.pageId != null && !reset.pageId.equals(activePageId)) {
                return;
            }

            slotsById.clear();
            activePageId = null;
            activeViewport = null;
        }
    }

    private boolean isValidFrame(GrapheneNativeSlotFramePayload frame) {
        return frame != null
                && frame.version == GrapheneNativeSlotChannels.VERSION
                && frame.pageId != null
                && !frame.pageId.isBlank()
                && frame.seq > 0L;
    }

    private boolean acceptSequenceLocked(String pageId, long sequence) {
        Long previousSequence = lastSequenceByPageId.get(pageId);
        if (previousSequence != null && sequence <= previousSequence) {
            return false;
        }

        lastSequenceByPageId.put(pageId, sequence);
        return true;
    }

    private void retireActivePageIdLocked() {
        if (activePageId == null || !retiredPageIds.add(activePageId)) {
            return;
        }

        retiredPageIdOrder.addLast(activePageId);
        while (retiredPageIdOrder.size() > MAX_RETIRED_PAGE_IDS) {
            String removedPageId = retiredPageIdOrder.removeFirst();
            retiredPageIds.remove(removedPageId);
            lastSequenceByPageId.remove(removedPageId);
        }
    }

    private List<GrapheneNativeSlot> snapshot() {
        synchronized (lock) {
            if (closed || slotsById.isEmpty()) {
                return List.of();
            }

            return slotsById.values().stream()
                    .sorted(Comparator.comparingInt(GrapheneNativeSlot::zIndex).thenComparing(GrapheneNativeSlot::id))
                    .toList();
        }
    }

    private GrapheneNativeSlotPointer pointer() {
        synchronized (lock) {
            return pointer;
        }
    }

    private GrapheneNativeSlotViewport viewport(int resolutionWidth, int resolutionHeight) {
        synchronized (lock) {
            if (activeViewport != null) {
                return activeViewport;
            }
        }

        return GrapheneNativeSlotViewport.fromResolution(resolutionWidth, resolutionHeight);
    }
}
