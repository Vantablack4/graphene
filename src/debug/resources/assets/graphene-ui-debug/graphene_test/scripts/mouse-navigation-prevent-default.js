(function () {
    const preventNonPrimaryCheckbox = document.getElementById("prevent-non-primary");
    const pushStateButton = document.getElementById("push-state");
    const captureTarget = document.getElementById("capture-target");
    const statusElement = document.getElementById("status");
    const eventLog = document.getElementById("event-log");
    const mouseEventTypes = ["pointerdown", "mousedown", "pointerup", "mouseup", "auxclick"];
    let popstateCount = 0;

    function currentStateIndex() {
        const index = Number(history.state?.grapheneTestIndex);
        return Number.isFinite(index) ? index : 0;
    }

    function setLog(text) {
        eventLog.textContent = text;
    }

    function appendLogLine(label, value) {
        const renderedValue = typeof value === "string" ? value : JSON.stringify(value, null, 2);
        eventLog.textContent = "[" + new Date().toISOString() + "] " + label + "\n" + renderedValue + "\n\n" + eventLog.textContent;
    }

    function updateStatus() {
        statusElement.textContent = "History index: " + currentStateIndex() + "\n"
            + "Popstate count: " + popstateCount + "\n"
            + "Prevent default: " + (preventNonPrimaryCheckbox.checked ? "on" : "off");
    }

    function ensureHistoryEntries() {
        const state = history.state;
        if (state?.grapheneMouseNavigationTest === true) {
            updateStatus();
            return;
        }

        history.replaceState({grapheneMouseNavigationTest: true, grapheneTestIndex: 0}, "", location.href);
        history.pushState({grapheneMouseNavigationTest: true, grapheneTestIndex: 1}, "", location.href);
        history.pushState({grapheneMouseNavigationTest: true, grapheneTestIndex: 2}, "", location.href);
        appendLogLine("history", "Seeded 3 history entries for back/forward testing.");
        updateStatus();
    }

    function handleMouseEvent(event) {
        if (preventNonPrimaryCheckbox.checked && event.button !== 0 && event.cancelable) {
            event.preventDefault();
        }

        appendLogLine(event.type, {
            button: event.button,
            buttons: event.buttons,
            cancelable: event.cancelable,
            defaultPrevented: event.defaultPrevented,
            target: event.target?.id || event.target?.tagName?.toLowerCase() || "unknown"
        });
        updateStatus();
    }

    function pushHistoryState() {
        const nextIndex = currentStateIndex() + 1;
        history.pushState({grapheneMouseNavigationTest: true, grapheneTestIndex: nextIndex}, "", location.href);
        appendLogLine("history.pushState", {grapheneTestIndex: nextIndex});
        updateStatus();
    }

    function installListeners() {
        mouseEventTypes.forEach(function (eventType) {
            document.addEventListener(eventType, handleMouseEvent, true);
        });

        globalThis.addEventListener("popstate", function (event) {
            popstateCount += 1;
            appendLogLine("popstate", event.state);
            updateStatus();
        });

        pushStateButton.addEventListener("click", pushHistoryState);
        preventNonPrimaryCheckbox.addEventListener("change", updateStatus);
        captureTarget.addEventListener("click", function () {
            appendLogLine("capture-target", "Primary click received.");
        });
    }

    installListeners();
    setLog("No events yet.");
    ensureHistoryEntries();
    captureTarget.focus();
    updateStatus();
})();
