(function () {
    const params = new URLSearchParams(location.search);

    async function refusedPathStatus() {
        try {
            const response = await fetch("./refused%5Cpath.js", { cache: "no-store" });
            return response.status;
        } catch (error) {
            return -1;
        }
    }

    function whenBridgeReady() {
        if (!globalThis.grapheneBridge) {
            setTimeout(whenBridgeReady, 20);
            return;
        }

        globalThis.grapheneBridge.ready().then(async function () {
            const report = { origin: location.origin, href: location.href, step: params.get("step") || "first" };
            if (report.step === "first") {
                report.refusedPathStatus = await refusedPathStatus();
            }

            await globalThis.grapheneBridge.emit("test:bundled", report);
            if (report.step === "first") {
                // Same origin, outside the bundled prefix: the network (here the test's stand-in) serves it.
                location.assign("/outside/network.html");
            }
        });
    }

    whenBridgeReady();
})();
