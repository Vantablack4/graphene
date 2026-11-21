package tytoo.grapheneuitest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import tytoo.grapheneui.api.GrapheneCore;

/**
 * With real CEF: only the main frame of the page Java opened may call the bridge. A same-origin iframe, a data: iframe
 * and their window.open popups are refused, and the popups never load into the bridged browser. A page served from
 * the jar under a web origin gets the bridge and keeps that origin.
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
        String pageUrl = GrapheneGameTestClient.BUNDLED_PAGE_PREFIX + "index.html?mode=test";
        context.setScreen(() -> new BridgeCallerScreen(pageUrl));
        waitForCall(context, "test:bundled", "the bundled page never reached the bridge");

        JsonObject payload = JsonParser.parseString(screen(context).payload("test:bundled")).getAsJsonObject();
        check(
                payload.get("origin").getAsString().equals("https://graphene-bundled.invalid"),
                "the bundled page did not keep its web origin: " + payload
        );
        System.out.println("BRIDGE_BUNDLED " + payload);
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

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
