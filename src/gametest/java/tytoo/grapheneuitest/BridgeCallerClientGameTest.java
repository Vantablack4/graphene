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
 * the jar under a web origin gets the bridge and keeps that origin; a same-origin page from the network does not, and
 * a refused path under the bundled prefix is answered locally instead of by the network.
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
        waitForCall(context, "test:bundled", "the bundled page never reached the bridge");

        BridgeCallerScreen screen = screen(context);
        List<String> failures = new ArrayList<>();
        JsonObject first = JsonParser.parseString(screen.payload("test:bundled")).getAsJsonObject();
        expect(
                failures,
                first.get("origin").getAsString().equals("https://graphene-bundled.invalid"),
                "the bundled page did not keep its web origin: " + first
        );
        expect(failures, first.get("refusedPathStatus").getAsInt() == 404, "a refused path under the prefix was not answered locally: " + first);
        expect(
                failures,
                network.requestedPaths().stream().noneMatch(path -> path.startsWith("/page/")),
                "a path under the bundled prefix reached the network: " + network.requestedPaths()
        );

        try {
            context.waitFor(client -> screen.calls("test:bundled") >= 2, PAGE_TIMEOUT_TICKS);
        } catch (AssertionError timeout) {
            throw new AssertionError("the page never came back from the network page; url=" + screen.currentUrl()
                    + " network=" + network.requestedPaths(), timeout);
        }

        JsonObject back = JsonParser.parseString(screen.payload("test:bundled")).getAsJsonObject();
        expect(failures, network.requestedPaths().contains(NetworkStandInSchemeHandlerFactory.NETWORK_PAGE_PATH), "the network page never loaded");
        expect(failures, screen.calls("test:outside") == 0, "a same-origin network page called the bridge " + screen.calls("test:outside") + " times");
        expect(failures, back.get("href").getAsString().contains("outside=" + REFUSED), "the network page's bridge call was not refused: " + back);
        System.out.println("BRIDGE_BUNDLED " + first + " then " + back + " network=" + network.requestedPaths());
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("; ", failures));
        }

        context.setScreen(() -> null);
        context.waitTicks(20);
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
