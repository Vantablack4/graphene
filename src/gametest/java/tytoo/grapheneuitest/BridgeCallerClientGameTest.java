package tytoo.grapheneuitest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.cef.CefApp;
import tytoo.grapheneui.api.GrapheneCore;

import java.util.ArrayList;
import java.util.List;

/**
 * With real CEF: only the main frame of the page Java opened may call the bridge. A same-origin iframe, a data: iframe
 * and their window.open popups are refused, and the popups never load into the bridged browser. A page served from
 * the jar on a Graphene-owned origin gets the bridge, reads a proxied catalog and draws a proxied image on a canvas it
 * can export; disallowed proxy paths, types and methods get local errors, and nothing on the origin reaches the network.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BridgeCallerClientGameTest implements FabricClientGameTest {
    private static final String CALLERS_PAGE = "web/bridge-callers/index.html";
    private static final int PAGE_TIMEOUT_TICKS = 600;
    private static final int POPUP_SETTLE_TICKS = 60;
    private static final int REFUSED = 403;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> GrapheneCore.isInitialized(), 2400);
        checkFramesAndPopupsAreRefused(context);
        checkBundledPageKeepsItsOriginAndBridge(context);
    }

    private static void checkFramesAndPopupsAreRefused(ClientGameTestContext context) {
        String pageUrl = GrapheneCore.handle(GrapheneGameTestClient.class).appAssets().asset(CALLERS_PAGE);
        context.setScreen(() -> new BridgeCallerScreen(pageUrl));
        waitForCall(context, "test:report", "the page never reported its frames' results");
        context.waitTicks(POPUP_SETTLE_TICKS);

        BridgeCallerScreen screen = screen(context);
        JsonObject report = JsonParser.parseString(screen.payload("test:report")).getAsJsonObject();
        for (String frame : new String[]{"same", "foreign"}) {
            JsonObject result = report.getAsJsonObject(frame);
            if (result.get("ok").getAsBoolean() || result.get("code").getAsInt() != REFUSED) {
                throw new AssertionError("The " + frame + " frame's bridge call was not refused: " + result);
            }
        }

        check(screen.calls("test:main") == 1, "the main frame's request reached Java " + screen.calls("test:main") + " times");
        check(screen.calls("test:frame") == 0, "a frame's event reached Java " + screen.calls("test:frame") + " times");
        check(screen.calls("test:popup") == 0, "a popup page called the bridge " + screen.calls("test:popup") + " times");
        check(pageUrl.equals(screen.currentUrl()), "a popup replaced the bridged page: " + screen.currentUrl());
        System.out.println("BRIDGE_CALLERS " + report);
        context.setScreen(() -> null);
        context.waitTicks(20);
    }

    private static void checkBundledPageKeepsItsOriginAndBridge(ClientGameTestContext context) {
        NetworkStandInSchemeHandlerFactory network = new NetworkStandInSchemeHandlerFactory();
        CefApp.getInstance().registerSchemeHandlerFactory("https", NetworkStandInSchemeHandlerFactory.HOST, network);
        String pageUrl = GrapheneGameTestClient.BUNDLED_PAGE_PREFIX + "index.html?mode=test";
        context.setScreen(() -> new BridgeCallerScreen(pageUrl));
        waitForCall(context, "test:bundled", "the page on the owned origin never reached the bridge");

        JsonObject report = JsonParser.parseString(screen(context).payload("test:bundled")).getAsJsonObject();
        List<String> upstream = GrapheneGameTestClient.UPSTREAM.requests();
        System.out.println("BRIDGE_BUNDLED " + report + " upstream=" + upstream + " network=" + network.requestedPaths());
        List<String> failures = new ArrayList<>();
        expect(failures, report.get("origin").getAsString().equals(GrapheneGameTestClient.OWNED_SITE), "the page lost its origin");
        expect(failures, report.get("catalogStatus").getAsInt() == 200, "the proxied catalog did not load");
        expect(failures, "application/json".equals(string(report, "catalogType")), "the proxy forwarded or lost the type: " + string(report, "catalogType"));
        expect(failures, "nosniff".equals(string(report, "catalogNosniff")), "no nosniff on a proxied asset");
        expect(failures, "sandbox".equals(string(report, "catalogCsp")), "no sandbox CSP on a proxied asset");
        expect(
                failures,
                report.has("catalog") && !report.get("catalog").isJsonNull()
                        && report.get("catalog").toString().equals(UpstreamAssetServer.CATALOG_JSON),
                "the catalog body changed"
        );
        expect(failures, report.has("exported") && report.get("exported").getAsBoolean(), "a proxied image tainted the canvas");
        expect(failures, report.has("pixel") && report.get("pixel").toString().equals("[255,0,0,255]"), "the proxied image did not draw");

        JsonObject statuses = report.getAsJsonObject("statuses");
        for (String refusedLocally : List.of("disallowedExtension", "query", "encodedBackslash", "refusedBundledPath", "outsideMounts")) {
            expect(failures, statuses.get(refusedLocally).getAsInt() == 404, refusedLocally + " was not refused locally");
        }
        expect(failures, statuses.get("upstreamError").getAsInt() == 502, "an upstream error was not a local 502");
        expect(failures, statuses.get("upstreamRedirect").getAsInt() == 502, "an upstream redirect was followed or passed on");
        expect(failures, statuses.get("post").getAsInt() == 405, "a POST was proxied");

        expect(failures, network.requestedPaths().isEmpty(), "a request on the owned origin reached the network: " + network.requestedPaths());
        expect(
                failures,
                upstream.stream().allMatch(request -> request.startsWith("GET ") && !request.contains("?") && !request.contains("cookie=")),
                "the proxy sent something other than a plain GET upstream: " + upstream
        );
        expect(
                failures,
                upstream.stream().noneMatch(request -> request.contains("page.html") || request.contains("a%5C") || request.contains("a\\")),
                "a refused path reached the upstream: " + upstream
        );
        expect(failures, upstream.contains("GET catalog.json") && upstream.contains("GET pixel.png"), "the proxy never fetched the catalog and image");
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("; ", failures));
        }

        context.setScreen(() -> null);
        context.waitTicks(20);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static void waitForCall(ClientGameTestContext context, String channel, String failure) {
        try {
            context.waitFor(client -> client.screen instanceof BridgeCallerScreen screen && screen.calls(channel) > 0, PAGE_TIMEOUT_TICKS);
        } catch (AssertionError timeout) {
            BridgeCallerScreen screen = screen(context);
            throw new AssertionError(failure + "; url=" + (screen == null ? null : screen.currentUrl()), timeout);
        }
    }

    private static BridgeCallerScreen screen(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.screen instanceof BridgeCallerScreen screen ? screen : null);
    }

    private static void expect(List<String> failures, boolean condition, String failure) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
