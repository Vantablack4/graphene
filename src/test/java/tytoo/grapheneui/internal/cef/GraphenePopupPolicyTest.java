package tytoo.grapheneui.internal.cef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraphenePopupPolicyTest {
    private static final String TARGET = "https://evil.example/popup.html";

    @Test
    void windowOpenFromASubframeIsBlockedAndNeverLoadsIntoTheBridgedBrowser() {
        List<String> loads = new ArrayList<>();

        boolean cancelled = new GrapheneCefLifeSpanHandler().onBeforePopup(browser(loads), frame(false), TARGET, "_blank");

        assertTrue(cancelled);
        assertEquals(List.of(), loads);
    }

    @Test
    void windowOpenFromTheMainFrameIsBlockedToo() {
        List<String> loads = new ArrayList<>();

        boolean cancelled = new GrapheneCefLifeSpanHandler().onBeforePopup(browser(loads), frame(true), TARGET, "_blank");

        assertTrue(cancelled);
        assertEquals(List.of(), loads);
    }

    @Test
    void popupsWithoutABrowserAreStillCancelled() {
        assertTrue(new GrapheneCefLifeSpanHandler().onBeforePopup(null, null, TARGET, "_blank"));
    }

    @Test
    void aSubframeCannotOpenATabIntoTheBridgedBrowser() {
        List<String> loads = new ArrayList<>();

        boolean cancelled = new GrapheneCefRequestHandler().onOpenURLFromTab(browser(loads), frame(false), TARGET, true);

        assertTrue(cancelled);
        assertEquals(List.of(), loads);
    }

    @Test
    void onlyTheMainFrameMayTurnATabOpenIntoItsOwnNavigation() {
        assertTrue(GrapheneCefRequestHandler.redirectsTabOpenIntoBrowser(true, TARGET));
        assertFalse(GrapheneCefRequestHandler.redirectsTabOpenIntoBrowser(false, TARGET));
        assertFalse(GrapheneCefRequestHandler.redirectsTabOpenIntoBrowser(true, " "));
        assertFalse(GrapheneCefRequestHandler.redirectsTabOpenIntoBrowser(true, null));
    }

    private static CefBrowser browser(List<String> loads) {
        return (CefBrowser) Proxy.newProxyInstance(
                GraphenePopupPolicyTest.class.getClassLoader(),
                new Class<?>[]{CefBrowser.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("loadURL")) {
                        loads.add((String) args[0]);
                    }

                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static CefFrame frame(boolean main) {
        return (CefFrame) Proxy.newProxyInstance(
                GraphenePopupPolicyTest.class.getClassLoader(),
                new Class<?>[]{CefFrame.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isMain" -> main;
                    case "getURL" -> main ? "app://assets/mod_manuscript/web/manuscript/index.html" : "https://evil.example/frame.html";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }

        if (returnType == int.class) {
            return 0;
        }

        if (returnType == long.class) {
            return 0L;
        }

        return null;
    }
}
