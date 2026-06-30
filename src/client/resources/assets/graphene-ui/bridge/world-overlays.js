(function () {
    if (globalThis.grapheneWorldOverlays?.__grapheneWorldOverlaysInstalled) {
        return;
    }

    const GRAPHENE_WORLD_OVERLAYS_INSTALLED_FLAG = "__grapheneWorldOverlaysInstalled";
    const GRAPHENE_WORLD_OVERLAYS_VERSION = 1;
    const GRAPHENE_WORLD_OVERLAYS_FRAME_CHANNEL = "graphene:world-overlays:v1:frame";
    const GRAPHENE_WORLD_OVERLAYS_RESET_CHANNEL = "graphene:world-overlays:v1:reset";
    const GRAPHENE_WORLD_OVERLAYS_RETRY_DELAY_MS = 50;
    const GRAPHENE_WORLD_OVERLAYS_DEFAULT_ORIGIN = "bottom-center";

    const grapheneWorldOverlaysListeners = new Set();
    const grapheneWorldOverlaysBindings = new Map();

    let grapheneWorldOverlaysSnapshot = grapheneWorldOverlaysEmptySnapshot();
    let grapheneWorldOverlaysBridgeRetryHandle = 0;
    let grapheneWorldOverlaysBridgeInstalled = false;

    function grapheneWorldOverlaysReportSuppressedError(context, error) {
        const consoleObject = globalThis.console;
        if (consoleObject && typeof consoleObject.debug === "function") {
            consoleObject.debug("[GrapheneWorldOverlays] " + context, error);
        }
    }

    function grapheneWorldOverlaysEmptySnapshot() {
        return {
            version: GRAPHENE_WORLD_OVERLAYS_VERSION,
            sequence: 0,
            guiWidth: 0,
            guiHeight: 0,
            partialTick: 0,
            camera: null,
            anchors: []
        };
    }

    function grapheneWorldOverlaysResolveBridge() {
        const bridge = globalThis.grapheneBridge;
        if (!bridge || typeof bridge.on !== "function") {
            return null;
        }

        return bridge;
    }

    function grapheneWorldOverlaysScheduleBridgeRetry() {
        if (grapheneWorldOverlaysBridgeRetryHandle || grapheneWorldOverlaysBridgeInstalled) {
            return;
        }

        if (typeof globalThis.setTimeout !== "function") {
            return;
        }

        grapheneWorldOverlaysBridgeRetryHandle = globalThis.setTimeout(function () {
            grapheneWorldOverlaysBridgeRetryHandle = 0;
            grapheneWorldOverlaysInstallBridgeListeners();
        }, GRAPHENE_WORLD_OVERLAYS_RETRY_DELAY_MS);
    }

    function grapheneWorldOverlaysInstallBridgeListeners() {
        if (grapheneWorldOverlaysBridgeInstalled) {
            return;
        }

        const bridge = grapheneWorldOverlaysResolveBridge();
        if (!bridge) {
            grapheneWorldOverlaysScheduleBridgeRetry();
            return;
        }

        bridge.on(GRAPHENE_WORLD_OVERLAYS_FRAME_CHANNEL, grapheneWorldOverlaysOnFrame);
        bridge.on(GRAPHENE_WORLD_OVERLAYS_RESET_CHANNEL, grapheneWorldOverlaysOnReset);
        grapheneWorldOverlaysBridgeInstalled = true;
    }

    function grapheneWorldOverlaysNormalizeFrame(frame) {
        if (!frame || typeof frame !== "object") {
            return grapheneWorldOverlaysEmptySnapshot();
        }

        const anchors = Array.isArray(frame.anchors)
            ? frame.anchors.filter(grapheneWorldOverlaysIsAnchorFrame).map(grapheneWorldOverlaysNormalizeAnchor)
            : [];

        return {
            version: GRAPHENE_WORLD_OVERLAYS_VERSION,
            sequence: Number(frame.sequence) || 0,
            guiWidth: Number(frame.guiWidth) || 0,
            guiHeight: Number(frame.guiHeight) || 0,
            partialTick: Number(frame.partialTick) || 0,
            camera: grapheneWorldOverlaysNormalizeCamera(frame.camera),
            anchors: anchors
        };
    }

    function grapheneWorldOverlaysNormalizeCamera(camera) {
        if (!camera || typeof camera !== "object") {
            return null;
        }

        return {
            dimension: typeof camera.dimension === "string" ? camera.dimension : "",
            x: Number(camera.x) || 0,
            y: Number(camera.y) || 0,
            z: Number(camera.z) || 0
        };
    }

    function grapheneWorldOverlaysIsAnchorFrame(anchor) {
        return Boolean(anchor)
            && typeof anchor === "object"
            && typeof anchor.id === "string"
            && anchor.id.trim().length > 0;
    }

    function grapheneWorldOverlaysNormalizeAnchor(anchor) {
        return {
            id: anchor.id,
            kind: typeof anchor.kind === "string" ? anchor.kind : "default",
            priority: Number(anchor.priority) || 0,
            interactive: Boolean(anchor.interactive),
            x: Number(anchor.x) || 0,
            y: Number(anchor.y) || 0,
            depth: Number(anchor.depth) || 0,
            distance: Number(anchor.distance) || 0,
            scale: Number(anchor.scale) || 1,
            alpha: Number(anchor.alpha) || 1,
            worldX: Number(anchor.worldX) || 0,
            worldY: Number(anchor.worldY) || 0,
            worldZ: Number(anchor.worldZ) || 0,
            payload: anchor.payload === undefined ? null : anchor.payload
        };
    }

    function grapheneWorldOverlaysOnFrame(frame) {
        if (!frame || Number(frame.version) !== GRAPHENE_WORLD_OVERLAYS_VERSION) {
            return;
        }

        grapheneWorldOverlaysSnapshot = grapheneWorldOverlaysNormalizeFrame(frame);
        grapheneWorldOverlaysApplyBindings();
        grapheneWorldOverlaysFlushNativeSlots("world-overlays-frame");
        grapheneWorldOverlaysNotifyListeners();
    }

    function grapheneWorldOverlaysOnReset(resetPayload) {
        if (resetPayload && Number(resetPayload.version) !== GRAPHENE_WORLD_OVERLAYS_VERSION) {
            return;
        }

        grapheneWorldOverlaysSnapshot = grapheneWorldOverlaysEmptySnapshot();
        grapheneWorldOverlaysApplyBindings();
        grapheneWorldOverlaysFlushNativeSlots("world-overlays-reset");
        grapheneWorldOverlaysNotifyListeners();
    }

    function grapheneWorldOverlaysFlushNativeSlots(reason) {
        const nativeSlots = globalThis.grapheneNativeSlots;
        if (!nativeSlots || typeof nativeSlots.flush !== "function") {
            return;
        }

        try {
            nativeSlots.flush(reason);
        } catch (error) {
            grapheneWorldOverlaysReportSuppressedError("Native slot flush failed", error);
        }
    }

    function grapheneWorldOverlaysNotifyListeners() {
        const snapshot = grapheneWorldOverlaysSnapshot;
        grapheneWorldOverlaysListeners.forEach(function (listener) {
            try {
                listener(snapshot);
            } catch (error) {
                grapheneWorldOverlaysReportSuppressedError("Frame listener failed", error);
            }
        });
    }

    function grapheneWorldOverlaysSubscribe(listener) {
        if (typeof listener !== "function") {
            throw new TypeError("listener must be a function");
        }

        grapheneWorldOverlaysListeners.add(listener);
        try {
            listener(grapheneWorldOverlaysSnapshot);
        } catch (error) {
            grapheneWorldOverlaysReportSuppressedError("Initial listener call failed", error);
        }

        return function () {
            grapheneWorldOverlaysListeners.delete(listener);
        };
    }

    function grapheneWorldOverlaysSnapshotCopy() {
        return {
            version: grapheneWorldOverlaysSnapshot.version,
            sequence: grapheneWorldOverlaysSnapshot.sequence,
            guiWidth: grapheneWorldOverlaysSnapshot.guiWidth,
            guiHeight: grapheneWorldOverlaysSnapshot.guiHeight,
            partialTick: grapheneWorldOverlaysSnapshot.partialTick,
            camera: grapheneWorldOverlaysSnapshot.camera
                ? Object.assign({}, grapheneWorldOverlaysSnapshot.camera)
                : null,
            anchors: grapheneWorldOverlaysSnapshot.anchors.map(function (anchor) {
                return Object.assign({}, anchor);
            })
        };
    }

    function grapheneWorldOverlaysAnchor(id) {
        if (typeof id !== "string") {
            return null;
        }

        return grapheneWorldOverlaysSnapshot.anchors.find(function (anchor) {
            return anchor.id === id;
        }) || null;
    }

    function grapheneWorldOverlaysBindElement(id, element, options) {
        if (typeof id !== "string" || id.trim().length === 0) {
            throw new TypeError("id must be a non-empty string");
        }

        if (!element || typeof element.getBoundingClientRect !== "function" || !element.style) {
            throw new TypeError("element must be a DOM element");
        }

        const normalizedId = id.trim();
        const binding = {
            id: normalizedId,
            element: element,
            options: options && typeof options === "object" ? Object.assign({}, options) : {},
            display: element.style.display === "none" ? "" : element.style.display || ""
        };
        grapheneWorldOverlaysBindings.set(normalizedId, binding);
        grapheneWorldOverlaysApplyBinding(binding);

        return {
            id: normalizedId,
            update: function (nextOptions) {
                binding.options = nextOptions && typeof nextOptions === "object" ? Object.assign({}, nextOptions) : {};
                grapheneWorldOverlaysApplyBinding(binding);
                return this;
            },
            unbind: function () {
                return grapheneWorldOverlaysUnbindElement(normalizedId);
            }
        };
    }

    function grapheneWorldOverlaysUnbindElement(id) {
        const binding = grapheneWorldOverlaysBindings.get(id);
        if (!binding) {
            return false;
        }

        grapheneWorldOverlaysBindings.delete(id);
        return true;
    }

    function grapheneWorldOverlaysApplyBindings() {
        grapheneWorldOverlaysBindings.forEach(grapheneWorldOverlaysApplyBinding);
    }

    function grapheneWorldOverlaysApplyBinding(binding) {
        const anchor = grapheneWorldOverlaysAnchor(binding.id);
        const element = binding.element;
        if (!anchor) {
            element.style.display = "none";
            return;
        }

        const options = binding.options;
        const rect = element.getBoundingClientRect();
        const origin = grapheneWorldOverlaysResolveOrigin(options.origin);
        const offsetX = Number(options.offsetX) || 0;
        const offsetY = Number(options.offsetY) || 0;
        const x = anchor.x - rect.width * origin.x + offsetX;
        const y = anchor.y - rect.height * origin.y + offsetY;
        const scale = Number.isFinite(anchor.scale) ? anchor.scale : 1;

        element.style.display = binding.display;
        element.style.position = "absolute";
        element.style.left = "0";
        element.style.top = "0";
        element.style.transform = "translate3d(" + x.toFixed(3) + "px, " + y.toFixed(3) + "px, 0) scale(" + scale + ")";
        element.style.opacity = String(Math.max(0, Math.min(1, anchor.alpha)));
        element.style.pointerEvents = anchor.interactive || options.pointerEvents ? "" : "none";
    }

    function grapheneWorldOverlaysResolveOrigin(origin) {
        const normalizedOrigin = typeof origin === "string" && origin.trim().length > 0
            ? origin.trim().toLowerCase()
            : GRAPHENE_WORLD_OVERLAYS_DEFAULT_ORIGIN;

        if (normalizedOrigin === "top-left") {
            return {x: 0, y: 0};
        }
        if (normalizedOrigin === "top-center") {
            return {x: 0.5, y: 0};
        }
        if (normalizedOrigin === "top-right") {
            return {x: 1, y: 0};
        }
        if (normalizedOrigin === "center-left") {
            return {x: 0, y: 0.5};
        }
        if (normalizedOrigin === "center" || normalizedOrigin === "center-center") {
            return {x: 0.5, y: 0.5};
        }
        if (normalizedOrigin === "center-right") {
            return {x: 1, y: 0.5};
        }
        if (normalizedOrigin === "bottom-left") {
            return {x: 0, y: 1};
        }
        if (normalizedOrigin === "bottom-right") {
            return {x: 1, y: 1};
        }

        return {x: 0.5, y: 1};
    }

    function grapheneWorldOverlaysInstallLifecycle() {
        if (typeof globalThis.addEventListener !== "function") {
            return;
        }

        const reset = function () {
            grapheneWorldOverlaysOnReset({version: GRAPHENE_WORLD_OVERLAYS_VERSION});
        };
        globalThis.addEventListener("pagehide", reset);
        globalThis.addEventListener("beforeunload", reset);
    }

    const grapheneWorldOverlaysApi = {
        __grapheneWorldOverlaysInstalled: true,
        VERSION: GRAPHENE_WORLD_OVERLAYS_VERSION,
        FRAME_CHANNEL: GRAPHENE_WORLD_OVERLAYS_FRAME_CHANNEL,
        RESET_CHANNEL: GRAPHENE_WORLD_OVERLAYS_RESET_CHANNEL,
        subscribe: grapheneWorldOverlaysSubscribe,
        onFrame: grapheneWorldOverlaysSubscribe,
        snapshot: grapheneWorldOverlaysSnapshotCopy,
        anchor: grapheneWorldOverlaysAnchor,
        bindElement: grapheneWorldOverlaysBindElement,
        unbindElement: grapheneWorldOverlaysUnbindElement
    };

    globalThis[GRAPHENE_WORLD_OVERLAYS_INSTALLED_FLAG] = true;
    globalThis.grapheneWorldOverlays = grapheneWorldOverlaysApi;

    grapheneWorldOverlaysInstallBridgeListeners();
    grapheneWorldOverlaysInstallLifecycle();
})();
