(function () {
    async function status(url, options) {
        try {
            const response = await fetch(url, Object.assign({ cache: "no-store" }, options || {}));
            return response.status;
        } catch (error) {
            return -1;
        }
    }

    function loadImage(url) {
        return new Promise(function (resolve, reject) {
            const image = new Image();
            image.onload = function () { resolve(image); };
            image.onerror = function () { reject(new Error("image failed: " + url)); };
            image.src = url;
        });
    }

    async function probe() {
        // A cookie on the owned origin; the proxy must never pass it upstream.
        document.cookie = "probe=1; path=/; secure";
        const report = { origin: location.origin, href: location.href };

        const catalog = await fetch("../cdn/catalog.json", { cache: "no-store" });
        report.catalogStatus = catalog.status;
        report.catalogType = catalog.headers.get("content-type");
        report.catalogNosniff = catalog.headers.get("x-content-type-options");
        report.catalogCsp = catalog.headers.get("content-security-policy");
        try {
            report.catalog = catalog.ok ? await catalog.json() : null;
        } catch (error) {
            report.catalog = null;
            report.catalogError = String(error);
        }

        try {
            const image = await loadImage("../cdn/pixel.png");
            const canvas = document.createElement("canvas");
            canvas.width = image.naturalWidth;
            canvas.height = image.naturalHeight;
            const context = canvas.getContext("2d");
            context.drawImage(image, 0, 0);
            report.pixel = Array.from(context.getImageData(0, 0, 1, 1).data);
            report.exported = canvas.toDataURL("image/png").startsWith("data:image/png;base64,");
        } catch (error) {
            report.imageError = String(error);
        }

        report.statuses = {
            disallowedExtension: await status("../cdn/page.html"),
            query: await status("../cdn/catalog.json?v=1"),
            encodedBackslash: await status("../cdn/a%5Cb.json"),
            upstreamError: await status("../cdn/error.json"),
            upstreamRedirect: await status("../cdn/moved.png"),
            post: await status("../cdn/catalog.json", { method: "POST", body: "x" }),
            refusedBundledPath: await status("./refused%5Cpath.js"),
            outsideMounts: await status("/outside/network.html")
        };
        return report;
    }

    function whenBridgeReady() {
        if (!globalThis.grapheneBridge) {
            setTimeout(whenBridgeReady, 20);
            return;
        }

        globalThis.grapheneBridge.ready().then(async function () {
            globalThis.grapheneBridge.emit("test:bundled", await probe());
        });
    }

    whenBridgeReady();
})();
