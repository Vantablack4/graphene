(function () {
    function whenBridgeReady() {
        if (!globalThis.grapheneBridge) {
            setTimeout(whenBridgeReady, 20);
            return;
        }

        globalThis.grapheneBridge.ready().then(function () {
            globalThis.grapheneBridge.emit("test:bundled", { origin: location.origin, href: location.href });
        });
    }

    whenBridgeReady();
})();
