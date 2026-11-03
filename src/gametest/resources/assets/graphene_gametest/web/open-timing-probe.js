// Test-only: records when the page reaches the states that gate its panel, against performance.now().
(() => {
    const marks = {};
    const mark = (name) => {
        if (!(name in marks)) {
            marks[name] = performance.now();
        }
    };

    globalThis.__openTimingMarks = marks;
    mark("probe");
    document.addEventListener("DOMContentLoaded", () => mark("dom.ready"), {once: true});
    globalThis.addEventListener("load", () => mark("load"), {once: true});
    globalThis.addEventListener("graphene:surface", () => mark("attr.surfaceEvent"), {once: true});
    globalThis.addEventListener("pixeltext:settled", () => mark("pixeltext.settled"), {once: true});
    document.fonts.ready.then(() => mark("fonts.ready"));
    let fontLoads = 0;
    document.fonts.addEventListener("loading", () => mark("fonts.loading" + (++fontLoads)));
    document.fonts.addEventListener("loadingdone", () => mark("fonts.done" + fontLoads));

    const root = document.documentElement;
    new MutationObserver(() => {
        if (root.hasAttribute("data-graphene-surface")) {
            mark("attr.surface");
        }
        if (root.hasAttribute("data-ready")) {
            mark("attr.ready");
        }
        if (root.hasAttribute("data-graphene-surface") && root.hasAttribute("data-ready")) {
            mark("panel.visible");
        }
    }).observe(root, {attributes: true});

    const poll = () => {
        if (globalThis.grapheneBridge) {
            mark("bridge.global");
            return;
        }
        requestAnimationFrame(poll);
    };
    requestAnimationFrame(poll);
})();
