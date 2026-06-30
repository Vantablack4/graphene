(function () {
    if (globalThis.grapheneNativeSlots?.__grapheneNativeSlotsInstalled) {
        return;
    }

    const GRAPHENE_NATIVE_SLOTS_INSTALLED_FLAG = "__grapheneNativeSlotsInstalled";
    const GRAPHENE_NATIVE_SLOTS_DEBUG_FLAG = "__grapheneNativeSlotsDebug";
    const GRAPHENE_NATIVE_SLOTS_VERSION = 1;
    const GRAPHENE_NATIVE_SLOTS_FRAME_CHANNEL = "graphene:native-slots:v1:frame";
    const GRAPHENE_NATIVE_SLOTS_RESET_CHANNEL = "graphene:native-slots:v1:reset";
    const GRAPHENE_NATIVE_SLOTS_POINTER_CHANNEL = "graphene:native-slots:v1:pointer";
    const GRAPHENE_NATIVE_SLOTS_RETRY_DELAY_MS = 50;
    const GRAPHENE_NATIVE_SLOTS_DEFAULT_KIND = "default";

    const grapheneNativeSlotsRecords = new Map();
    const grapheneNativeSlotsRemovedIds = new Set();
    const grapheneNativeSlotsObservedElements = new Map();
    const grapheneNativeSlotsPageId = grapheneNativeSlotsCreatePageId();

    let grapheneNativeSlotsSequence = 0;
    let grapheneNativeSlotsFrameHandle = 0;
    let grapheneNativeSlotsFrameHandleIsAnimationFrame = false;
    let grapheneNativeSlotsPendingReason = null;
    let grapheneNativeSlotsBridgeRetryHandle = 0;
    let grapheneNativeSlotsResizeObserver = null;
    let grapheneNativeSlotsIntersectionObserver = null;
    let grapheneNativeSlotsLifecycleInstalled = false;
    let grapheneNativeSlotsCleanedUp = false;

    function grapheneNativeSlotsNoop() {
    }

    function grapheneNativeSlotsReportSuppressedError(context, error) {
        const consoleObject = globalThis.console;
        if (consoleObject && typeof consoleObject.debug === "function") {
            consoleObject.debug("[GrapheneNativeSlots] " + context, error);
        }
    }

    function grapheneNativeSlotsWarn(message) {
        const consoleObject = globalThis.console;
        if (consoleObject && typeof consoleObject.warn === "function") {
            consoleObject.warn("[GrapheneNativeSlots] " + message);
        }
    }

    function grapheneNativeSlotsDebug(message) {
        if (globalThis[GRAPHENE_NATIVE_SLOTS_DEBUG_FLAG] !== true) {
            return;
        }

        const consoleObject = globalThis.console;
        if (consoleObject && typeof consoleObject.debug === "function") {
            consoleObject.debug("[GrapheneNativeSlots] " + message);
        }
    }

    function grapheneNativeSlotsCreatePageId() {
        const cryptoObject = globalThis.crypto;
        if (cryptoObject && typeof cryptoObject.randomUUID === "function") {
            return cryptoObject.randomUUID();
        }

        return "page-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
    }

    function grapheneNativeSlotsNextSequence() {
        grapheneNativeSlotsSequence += 1;
        return grapheneNativeSlotsSequence;
    }

    function grapheneNativeSlotsNormalizeId(id) {
        if (typeof id !== "string") {
            return null;
        }

        const trimmedId = id.trim();
        return trimmedId.length > 0 ? trimmedId : null;
    }

    function grapheneNativeSlotsNormalizeKind(kind) {
        if (typeof kind !== "string") {
            return GRAPHENE_NATIVE_SLOTS_DEFAULT_KIND;
        }

        const trimmedKind = kind.trim();
        return trimmedKind.length > 0 ? trimmedKind : GRAPHENE_NATIVE_SLOTS_DEFAULT_KIND;
    }

    function grapheneNativeSlotsNormalizeData(value) {
        return value === undefined ? null : value;
    }

    function grapheneNativeSlotsHasOwn(objectValue, key) {
        return Object.hasOwn(objectValue, key);
    }

    function grapheneNativeSlotsIsObject(value) {
        return Boolean(value) && typeof value === "object";
    }

    function grapheneNativeSlotsIsElement(value) {
        return Boolean(value) && value.nodeType === 1 && typeof value.getBoundingClientRect === "function";
    }

    function grapheneNativeSlotsIsUpdateOptions(value) {
        if (!grapheneNativeSlotsIsObject(value) || Array.isArray(value)) {
            return false;
        }

        return grapheneNativeSlotsHasOwn(value, "kind")
            || grapheneNativeSlotsHasOwn(value, "payload")
            || grapheneNativeSlotsHasOwn(value, "render")
            || grapheneNativeSlotsHasOwn(value, "handoff")
            || grapheneNativeSlotsHasOwn(value, "element");
    }

    function grapheneNativeSlotsResolveBridge() {
        const bridge = globalThis.grapheneBridge;
        if (!bridge || typeof bridge.emit !== "function") {
            return null;
        }

        return bridge;
    }

    function grapheneNativeSlotsEmit(channel, payload) {
        const bridge = grapheneNativeSlotsResolveBridge();
        if (!bridge) {
            grapheneNativeSlotsDebug("Bridge is unavailable; native slot message was not emitted");
            grapheneNativeSlotsScheduleBridgeRetry();
            return false;
        }

        try {
            const result = bridge.emit(channel, payload);
            if (result && typeof result.catch === "function") {
                result.catch(function (error) {
                    grapheneNativeSlotsReportSuppressedError("Bridge emit failed for channel '" + channel + "'", error);
                });
            }

            return true;
        } catch (error) {
            grapheneNativeSlotsReportSuppressedError("Bridge emit threw for channel '" + channel + "'", error);
            return false;
        }
    }

    function grapheneNativeSlotsScheduleBridgeRetry() {
        if (grapheneNativeSlotsBridgeRetryHandle || grapheneNativeSlotsCleanedUp) {
            return;
        }

        if (grapheneNativeSlotsRecords.size === 0 && grapheneNativeSlotsRemovedIds.size === 0) {
            return;
        }

        if (typeof globalThis.setTimeout !== "function") {
            return;
        }

        grapheneNativeSlotsBridgeRetryHandle = globalThis.setTimeout(function () {
            grapheneNativeSlotsBridgeRetryHandle = 0;
            grapheneNativeSlotsFlush("bridge-retry");
        }, GRAPHENE_NATIVE_SLOTS_RETRY_DELAY_MS);
    }

    function grapheneNativeSlotsInstallLifecycle() {
        if (grapheneNativeSlotsLifecycleInstalled) {
            return;
        }

        grapheneNativeSlotsLifecycleInstalled = true;

        if (typeof document !== "undefined" && typeof document.addEventListener === "function") {
            document.addEventListener("scroll", grapheneNativeSlotsOnLayoutChanged, true);
        }

        if (typeof globalThis.addEventListener === "function") {
            globalThis.addEventListener("resize", grapheneNativeSlotsOnLayoutChanged);
            globalThis.addEventListener("pagehide", grapheneNativeSlotsOnPageHide);
            globalThis.addEventListener("beforeunload", grapheneNativeSlotsOnBeforeUnload);
        }

        const visualViewport = globalThis.visualViewport;
        if (visualViewport && typeof visualViewport.addEventListener === "function") {
            visualViewport.addEventListener("resize", grapheneNativeSlotsOnLayoutChanged);
            visualViewport.addEventListener("scroll", grapheneNativeSlotsOnLayoutChanged);
        }
    }

    function grapheneNativeSlotsUninstallLifecycle() {
        if (!grapheneNativeSlotsLifecycleInstalled) {
            return;
        }

        grapheneNativeSlotsLifecycleInstalled = false;

        if (typeof document !== "undefined" && typeof document.removeEventListener === "function") {
            document.removeEventListener("scroll", grapheneNativeSlotsOnLayoutChanged, true);
        }

        if (typeof globalThis.removeEventListener === "function") {
            globalThis.removeEventListener("resize", grapheneNativeSlotsOnLayoutChanged);
            globalThis.removeEventListener("pagehide", grapheneNativeSlotsOnPageHide);
            globalThis.removeEventListener("beforeunload", grapheneNativeSlotsOnBeforeUnload);
        }

        const visualViewport = globalThis.visualViewport;
        if (visualViewport && typeof visualViewport.removeEventListener === "function") {
            visualViewport.removeEventListener("resize", grapheneNativeSlotsOnLayoutChanged);
            visualViewport.removeEventListener("scroll", grapheneNativeSlotsOnLayoutChanged);
        }
    }

    function grapheneNativeSlotsEnsureObservers() {
        if (!grapheneNativeSlotsResizeObserver && typeof globalThis.ResizeObserver === "function") {
            grapheneNativeSlotsResizeObserver = new ResizeObserver(function () {
                grapheneNativeSlotsScheduleFlush("resize-observer");
            });
        }

        if (!grapheneNativeSlotsIntersectionObserver && typeof globalThis.IntersectionObserver === "function") {
            grapheneNativeSlotsIntersectionObserver = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    grapheneNativeSlotsRecords.forEach(function (record) {
                        if (record.element === entry.target) {
                            record.intersection = {
                                isIntersecting: Boolean(entry.isIntersecting),
                                ratio: Number(entry.intersectionRatio) || 0
                            };
                        }
                    });
                });
                grapheneNativeSlotsScheduleFlush("intersection-observer");
            });
        }
    }

    function grapheneNativeSlotsObserveElement(element) {
        if (!grapheneNativeSlotsIsElement(element)) {
            return;
        }

        grapheneNativeSlotsEnsureObservers();

        const observedCount = grapheneNativeSlotsObservedElements.get(element) || 0;
        grapheneNativeSlotsObservedElements.set(element, observedCount + 1);
        if (observedCount > 0) {
            return;
        }

        if (grapheneNativeSlotsResizeObserver) {
            try {
                grapheneNativeSlotsResizeObserver.observe(element);
            } catch (error) {
                grapheneNativeSlotsReportSuppressedError("ResizeObserver observe failed", error);
            }
        }

        if (grapheneNativeSlotsIntersectionObserver) {
            try {
                grapheneNativeSlotsIntersectionObserver.observe(element);
            } catch (error) {
                grapheneNativeSlotsReportSuppressedError("IntersectionObserver observe failed", error);
            }
        }
    }

    function grapheneNativeSlotsUnobserveElement(element) {
        if (!grapheneNativeSlotsIsElement(element)) {
            return;
        }

        const observedCount = grapheneNativeSlotsObservedElements.get(element) || 0;
        if (observedCount > 1) {
            grapheneNativeSlotsObservedElements.set(element, observedCount - 1);
            return;
        }

        grapheneNativeSlotsObservedElements.delete(element);

        if (grapheneNativeSlotsResizeObserver) {
            try {
                grapheneNativeSlotsResizeObserver.unobserve(element);
            } catch (error) {
                grapheneNativeSlotsReportSuppressedError("ResizeObserver unobserve failed", error);
            }
        }

        if (grapheneNativeSlotsIntersectionObserver) {
            try {
                grapheneNativeSlotsIntersectionObserver.unobserve(element);
            } catch (error) {
                grapheneNativeSlotsReportSuppressedError("IntersectionObserver unobserve failed", error);
            }
        }
    }

    function grapheneNativeSlotsDisconnectObservers() {
        if (grapheneNativeSlotsResizeObserver) {
            grapheneNativeSlotsResizeObserver.disconnect();
        }

        if (grapheneNativeSlotsIntersectionObserver) {
            grapheneNativeSlotsIntersectionObserver.disconnect();
        }

        grapheneNativeSlotsObservedElements.clear();
    }

    function grapheneNativeSlotsOnLayoutChanged() {
        grapheneNativeSlotsScheduleFlush("layout");
    }

    function grapheneNativeSlotsOnPageHide() {
        grapheneNativeSlotsCleanup("pagehide");
    }

    function grapheneNativeSlotsOnBeforeUnload() {
        grapheneNativeSlotsCleanup("beforeunload");
    }

    function grapheneNativeSlotsCancelScheduledFlush() {
        if (!grapheneNativeSlotsFrameHandle) {
            return;
        }

        if (grapheneNativeSlotsFrameHandleIsAnimationFrame && typeof globalThis.cancelAnimationFrame === "function") {
            globalThis.cancelAnimationFrame(grapheneNativeSlotsFrameHandle);
        } else if (!grapheneNativeSlotsFrameHandleIsAnimationFrame && typeof globalThis.clearTimeout === "function") {
            globalThis.clearTimeout(grapheneNativeSlotsFrameHandle);
        }

        grapheneNativeSlotsFrameHandle = 0;
        grapheneNativeSlotsFrameHandleIsAnimationFrame = false;
    }

    function grapheneNativeSlotsScheduleFlush(reason) {
        if (grapheneNativeSlotsCleanedUp) {
            return;
        }

        grapheneNativeSlotsPendingReason = grapheneNativeSlotsPendingReason || reason || "scheduled";

        if (grapheneNativeSlotsFrameHandle) {
            return;
        }

        if (typeof globalThis.requestAnimationFrame === "function") {
            grapheneNativeSlotsFrameHandleIsAnimationFrame = true;
            grapheneNativeSlotsFrameHandle = globalThis.requestAnimationFrame(function () {
                grapheneNativeSlotsFrameHandle = 0;
                grapheneNativeSlotsFrameHandleIsAnimationFrame = false;
                const flushReason = grapheneNativeSlotsPendingReason || "animation-frame";
                grapheneNativeSlotsPendingReason = null;
                grapheneNativeSlotsFlush(flushReason);
            });
            return;
        }

        if (typeof globalThis.setTimeout === "function") {
            grapheneNativeSlotsFrameHandleIsAnimationFrame = false;
            grapheneNativeSlotsFrameHandle = globalThis.setTimeout(function () {
                grapheneNativeSlotsFrameHandle = 0;
                const flushReason = grapheneNativeSlotsPendingReason || "timeout";
                grapheneNativeSlotsPendingReason = null;
                grapheneNativeSlotsFlush(flushReason);
            }, 16);
        }
    }

    function grapheneNativeSlotsCreateRecord(id, options) {
        const record = {
            id: id,
            kind: grapheneNativeSlotsNormalizeKind(options.kind),
            payload: grapheneNativeSlotsNormalizeData(options.payload),
            render: grapheneNativeSlotsNormalizeData(options.render),
            handoff: grapheneNativeSlotsNormalizeData(options.handoff),
            element: null,
            intersection: null,
            api: null
        };

        record.api = grapheneNativeSlotsCreateSlotApi(record);
        return record;
    }

    function grapheneNativeSlotsCreateSlotApi(record) {
        return {
            id: record.id,
            attach: function (element) {
                grapheneNativeSlotsAttach(record.id, element);
            },
            update: function (options) {
                return grapheneNativeSlotsUpdate(record.id, options);
            },
            unregister: function () {
                return grapheneNativeSlotsUnregister(record.id);
            },
            flush: function (reason) {
                return grapheneNativeSlotsFlush(reason || "slot");
            },
            measureNow: function () {
                return grapheneNativeSlotsMeasureNow(record.id);
            }
        };
    }

    function grapheneNativeSlotsCreateNoopSlotApi(id) {
        return {
            id: id,
            attach: grapheneNativeSlotsNoop,
            update: function () {
                return this;
            },
            unregister: function () {
                return false;
            },
            flush: function () {
                return null;
            },
            measureNow: function () {
                return null;
            }
        };
    }

    function grapheneNativeSlotsApplyOptions(record, options) {
        if (grapheneNativeSlotsHasOwn(options, "kind")) {
            record.kind = grapheneNativeSlotsNormalizeKind(options.kind);
        }

        if (grapheneNativeSlotsHasOwn(options, "payload")) {
            record.payload = grapheneNativeSlotsNormalizeData(options.payload);
        }

        if (grapheneNativeSlotsHasOwn(options, "render")) {
            record.render = grapheneNativeSlotsNormalizeData(options.render);
        }

        if (grapheneNativeSlotsHasOwn(options, "handoff")) {
            record.handoff = grapheneNativeSlotsNormalizeData(options.handoff);
        }

        if (grapheneNativeSlotsHasOwn(options, "element")) {
            grapheneNativeSlotsAttach(record.id, options.element);
        }
    }

    function grapheneNativeSlotsAttach(id, element) {
        const record = grapheneNativeSlotsRecords.get(id);
        if (!record) {
            grapheneNativeSlotsWarn("Cannot attach unknown native slot '" + id + "'");
            return;
        }

        if (element !== null && element !== undefined && !grapheneNativeSlotsIsElement(element)) {
            grapheneNativeSlotsWarn("attach(element) expected a DOM element or null for native slot '" + id + "'");
            return;
        }

        if (record.element === element) {
            return;
        }

        if (record.element) {
            grapheneNativeSlotsUnobserveElement(record.element);
        }

        record.element = element || null;
        record.intersection = null;

        if (record.element) {
            grapheneNativeSlotsObserveElement(record.element);
        }

        grapheneNativeSlotsScheduleFlush("attach");
    }

    function grapheneNativeSlotsRegister(options) {
        if (!grapheneNativeSlotsIsObject(options)) {
            grapheneNativeSlotsWarn("register(options) expected an object");
            return grapheneNativeSlotsCreateNoopSlotApi(null);
        }

        const id = grapheneNativeSlotsNormalizeId(options.id);
        if (!id) {
            grapheneNativeSlotsWarn("register(options) requires a non-empty string id");
            return grapheneNativeSlotsCreateNoopSlotApi(null);
        }

        grapheneNativeSlotsInstallLifecycle();

        const existingRecord = grapheneNativeSlotsRecords.get(id);
        if (existingRecord) {
            grapheneNativeSlotsApplyOptions(existingRecord, options);
            grapheneNativeSlotsScheduleFlush("register-update");
            return existingRecord.api;
        }

        const record = grapheneNativeSlotsCreateRecord(id, options);
        grapheneNativeSlotsRecords.set(id, record);
        grapheneNativeSlotsRemovedIds.delete(id);

        if (grapheneNativeSlotsHasOwn(options, "element")) {
            grapheneNativeSlotsAttach(id, options.element);
        }

        grapheneNativeSlotsScheduleFlush("register");
        return record.api;
    }

    function grapheneNativeSlotsUpdate(id, options) {
        const normalizedId = grapheneNativeSlotsNormalizeId(id);
        if (!normalizedId) {
            grapheneNativeSlotsWarn("update(id, options) requires a non-empty string id");
            return null;
        }

        const record = grapheneNativeSlotsRecords.get(normalizedId);
        if (!record) {
            grapheneNativeSlotsWarn("Cannot update unknown native slot '" + normalizedId + "'");
            return null;
        }

        if (grapheneNativeSlotsIsUpdateOptions(options)) {
            grapheneNativeSlotsApplyOptions(record, options);
        } else {
            record.payload = grapheneNativeSlotsNormalizeData(options);
        }

        grapheneNativeSlotsScheduleFlush("update");
        return record.api;
    }

    function grapheneNativeSlotsUnregister(id) {
        const normalizedId = grapheneNativeSlotsNormalizeId(id);
        if (!normalizedId) {
            grapheneNativeSlotsWarn("unregister(id) requires a non-empty string id");
            return false;
        }

        const record = grapheneNativeSlotsRecords.get(normalizedId);
        if (!record) {
            return false;
        }

        if (record.element) {
            grapheneNativeSlotsUnobserveElement(record.element);
        }

        grapheneNativeSlotsRecords.delete(normalizedId);
        grapheneNativeSlotsRemovedIds.add(normalizedId);
        grapheneNativeSlotsScheduleFlush("unregister");
        return true;
    }

    function grapheneNativeSlotsReset(reason) {
        grapheneNativeSlotsCancelScheduledFlush();

        grapheneNativeSlotsRecords.forEach(function (record) {
            if (record.element) {
                grapheneNativeSlotsUnobserveElement(record.element);
            }
        });

        grapheneNativeSlotsRecords.clear();
        grapheneNativeSlotsRemovedIds.clear();
        grapheneNativeSlotsPendingReason = null;

        const resetPayload = {
            version: GRAPHENE_NATIVE_SLOTS_VERSION,
            pageId: grapheneNativeSlotsPageId,
            seq: grapheneNativeSlotsNextSequence(),
            reason: reason || "reset"
        };

        if (!grapheneNativeSlotsEmit(GRAPHENE_NATIVE_SLOTS_RESET_CHANNEL, resetPayload)) {
            return null;
        }

        return resetPayload;
    }

    function grapheneNativeSlotsCleanup(reason) {
        if (grapheneNativeSlotsCleanedUp) {
            return;
        }

        grapheneNativeSlotsCleanedUp = true;
        grapheneNativeSlotsReset(reason || "cleanup");
        grapheneNativeSlotsDisconnectObservers();
        grapheneNativeSlotsUninstallLifecycle();

        if (grapheneNativeSlotsBridgeRetryHandle && typeof globalThis.clearTimeout === "function") {
            globalThis.clearTimeout(grapheneNativeSlotsBridgeRetryHandle);
        }

        grapheneNativeSlotsBridgeRetryHandle = 0;
    }

    function grapheneNativeSlotsViewportSnapshot() {
        const visualViewport = globalThis.visualViewport;
        const documentElement = typeof document !== "undefined" ? document.documentElement : null;

        return {
            width: grapheneNativeSlotsRoundNumber(visualViewport?.width ?? globalThis.innerWidth ?? documentElement?.clientWidth ?? 0),
            height: grapheneNativeSlotsRoundNumber(visualViewport?.height ?? globalThis.innerHeight ?? documentElement?.clientHeight ?? 0),
            offsetLeft: grapheneNativeSlotsRoundNumber(visualViewport?.offsetLeft ?? 0),
            offsetTop: grapheneNativeSlotsRoundNumber(visualViewport?.offsetTop ?? 0),
            pageLeft: grapheneNativeSlotsRoundNumber(visualViewport?.pageLeft ?? globalThis.scrollX ?? 0),
            pageTop: grapheneNativeSlotsRoundNumber(visualViewport?.pageTop ?? globalThis.scrollY ?? 0),
            scale: grapheneNativeSlotsRoundNumber(visualViewport?.scale ?? 1)
        };
    }

    function grapheneNativeSlotsRoundNumber(value) {
        const numericValue = Number(value);
        if (!Number.isFinite(numericValue)) {
            return 0;
        }

        return Math.round(numericValue * 1000) / 1000;
    }

    function grapheneNativeSlotsRectSnapshot(rect) {
        return {
            x: grapheneNativeSlotsRoundNumber(rect.x),
            y: grapheneNativeSlotsRoundNumber(rect.y),
            left: grapheneNativeSlotsRoundNumber(rect.left),
            top: grapheneNativeSlotsRoundNumber(rect.top),
            right: grapheneNativeSlotsRoundNumber(rect.right),
            bottom: grapheneNativeSlotsRoundNumber(rect.bottom),
            width: grapheneNativeSlotsRoundNumber(rect.width),
            height: grapheneNativeSlotsRoundNumber(rect.height)
        };
    }

    function grapheneNativeSlotsMeasureRecord(record) {
        const element = record.element;
        const attached = grapheneNativeSlotsIsElement(element);
        const connected = attached && Boolean(element.isConnected);
        let rect = null;
        let hasArea = false;

        if (connected) {
            try {
                rect = grapheneNativeSlotsRectSnapshot(element.getBoundingClientRect());
                hasArea = rect.width > 0 && rect.height > 0;
            } catch (error) {
                grapheneNativeSlotsReportSuppressedError("Slot measurement failed for '" + record.id + "'", error);
                rect = null;
            }
        }

        const intersection = record.intersection;
        const visible = connected && hasArea && (intersection ? intersection.isIntersecting : true);

        return {
            id: record.id,
            kind: record.kind,
            payload: grapheneNativeSlotsNormalizeData(record.payload),
            render: grapheneNativeSlotsNormalizeData(record.render),
            handoff: grapheneNativeSlotsNormalizeData(record.handoff),
            attached: attached,
            connected: connected,
            visible: visible,
            intersectionRatio: intersection ? grapheneNativeSlotsRoundNumber(intersection.ratio) : null,
            rect: rect
        };
    }

    function grapheneNativeSlotsCreateFramePayload(reason) {
        const slots = [];
        grapheneNativeSlotsRecords.forEach(function (record) {
            slots.push(grapheneNativeSlotsMeasureRecord(record));
        });

        slots.sort(function (left, right) {
            return left.id.localeCompare(right.id);
        });

        return {
            version: GRAPHENE_NATIVE_SLOTS_VERSION,
            pageId: grapheneNativeSlotsPageId,
            seq: grapheneNativeSlotsNextSequence(),
            reason: reason || "flush",
            viewport: grapheneNativeSlotsViewportSnapshot(),
            slots: slots,
            removed: Array.from(grapheneNativeSlotsRemovedIds).sort()
        };
    }

    function grapheneNativeSlotsFlush(reason) {
        grapheneNativeSlotsCancelScheduledFlush();
        grapheneNativeSlotsPendingReason = null;

        if (grapheneNativeSlotsRecords.size === 0 && grapheneNativeSlotsRemovedIds.size === 0) {
            return null;
        }

        const framePayload = grapheneNativeSlotsCreateFramePayload(reason);
        if (!grapheneNativeSlotsEmit(GRAPHENE_NATIVE_SLOTS_FRAME_CHANNEL, framePayload)) {
            return null;
        }

        grapheneNativeSlotsRemovedIds.clear();
        return framePayload;
    }

    function grapheneNativeSlotsMeasureNow(id) {
        const normalizedId = grapheneNativeSlotsNormalizeId(id);
        if (!normalizedId) {
            grapheneNativeSlotsWarn("measureNow(id) requires a non-empty string id");
            return null;
        }

        const record = grapheneNativeSlotsRecords.get(normalizedId);
        if (!record) {
            grapheneNativeSlotsWarn("Cannot measure unknown native slot '" + normalizedId + "'");
            return null;
        }

        return grapheneNativeSlotsMeasureRecord(record);
    }

    function grapheneNativeSlotsInstallApi() {
        globalThis.grapheneNativeSlots = {
            __grapheneNativeSlotsInstalled: true,
            VERSION: GRAPHENE_NATIVE_SLOTS_VERSION,
            FRAME_CHANNEL: GRAPHENE_NATIVE_SLOTS_FRAME_CHANNEL,
            RESET_CHANNEL: GRAPHENE_NATIVE_SLOTS_RESET_CHANNEL,
            POINTER_CHANNEL: GRAPHENE_NATIVE_SLOTS_POINTER_CHANNEL,
            pageId: grapheneNativeSlotsPageId,
            register: grapheneNativeSlotsRegister,
            update: grapheneNativeSlotsUpdate,
            unregister: grapheneNativeSlotsUnregister,
            reset: function () {
                return grapheneNativeSlotsReset("api");
            },
            flush: function (reason) {
                return grapheneNativeSlotsFlush(reason || "api");
            },
            measureNow: grapheneNativeSlotsMeasureNow
        };
    }

    grapheneNativeSlotsInstallApi();
})();
