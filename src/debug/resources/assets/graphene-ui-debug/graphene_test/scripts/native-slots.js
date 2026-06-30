(function () {
    let slots = null;
    let motionEnabled = true;
    let itemLarge = false;

    function statusElement() {
        return document.getElementById("native-slot-status");
    }

    function logElement() {
        return document.getElementById("native-slot-log");
    }

    function appendLog(label, value) {
        const rendered = typeof value === "string" ? value : JSON.stringify(value, null, 2);
        logElement().textContent = "[" + new Date().toISOString() + "] " + label + "\n" + rendered + "\n\n" + logElement().textContent;
    }

    function nativeSlots() {
        const candidate = globalThis.grapheneNativeSlots;
        if (!candidate || typeof candidate.register !== "function") {
            return null;
        }

        return candidate;
    }

    function registerSlot(id, kind, payload, render, handoff) {
        const element = document.getElementById(id);
        const slot = slots.register({
            id: id,
            kind: kind,
            payload: payload,
            render: render || {},
            handoff: handoff || {},
            element: element
        });

        slot.attach(element);
        return slot;
    }

    function installSlots() {
        registerSlot(
            "native-item",
            "item",
            {item: "minecraft:diamond_sword", count: 1},
            {decorations: true},
            {tooltip: true}
        );
        registerSlot(
            "native-block",
            "block",
            {block: "minecraft:grass_block"},
            {decorations: false},
            {tooltip: true}
        );
        registerSlot(
            "native-head",
            "head",
            {uuid: "8667ba71-b85a-4004-af54-457a9734eed7"},
            {hat: true},
            {}
        );
        registerSlot(
            "native-skin",
            "skin",
            {uuid: "8667ba71-b85a-4004-af54-457a9734eed7"},
            {rotationX: -8, rotationY: 25},
            {}
        );
        registerSlot(
            "native-entity",
            "entity",
            {entity: "minecraft:zombie"},
            {followMouse: true},
            {}
        );
        registerSlot(
            "native-moving",
            "item",
            {item: "minecraft:emerald", count: 12},
            {decorations: true, zIndex: 10},
            {tooltip: true}
        );

        slots.flush("debug-install");
        appendLog("Registered native slots", {
            pageId: slots.pageId,
            frameChannel: slots.FRAME_CHANNEL
        });
    }

    function connectWhenAvailable() {
        slots = nativeSlots();
        if (!slots) {
            setTimeout(connectWhenAvailable, 50);
            return;
        }

        statusElement().textContent = "Native slot bridge: connected";
        installSlots();
    }

    function toggleMotion() {
        motionEnabled = !motionEnabled;
        document.body.classList.toggle("motion-paused", !motionEnabled);
        slots.flush("toggle-motion");
    }

    function resizeItem() {
        itemLarge = !itemLarge;
        document.getElementById("native-item").classList.toggle("native-slot-large", itemLarge);
        slots.flush("resize-item");
    }

    function flushSlots() {
        appendLog("Manual flush", slots.flush("manual") || "No frame emitted");
    }

    document.getElementById("toggle-motion").addEventListener("click", toggleMotion);
    document.getElementById("resize-item").addEventListener("click", resizeItem);
    document.getElementById("flush-slots").addEventListener("click", flushSlots);
    connectWhenAvailable();
})();
