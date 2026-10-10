package tytoo.grapheneui.internal.input.keyboard;

import org.cef.input.CefKeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneWindowsKeyEventPlatformResolverTest {
    private static final int WINDOWS_VK_DELETE = 0x2E;
    private static final int GLFW_WINDOWS_DELETE_SCAN_CODE = 0x153;
    private static final int WINDOWS_DELETE_SCAN_CODE = 83;

    private final GrapheneWindowsKeyEventPlatformResolver resolver = new GrapheneWindowsKeyEventPlatformResolver();

    @Test
    void deleteKeyDownUsesWindowsDeleteVirtualKey() {
        CefKeyEvent keyEvent = createDeleteKeyEvent(true);

        assertEquals(CefKeyEvent.KEYEVENT_RAWKEYDOWN, keyEvent.type);
        assertEquals(WINDOWS_VK_DELETE, keyEvent.windows_key_code);
        assertEquals(WINDOWS_DELETE_SCAN_CODE, keyEvent.scan_code);
        assertEquals(
                CefKeyEvent.buildWindowsNativeKeyCode(WINDOWS_DELETE_SCAN_CODE, true, false),
                keyEvent.native_key_code
        );
    }

    @Test
    void deleteKeyUpUsesWindowsDeleteVirtualKey() {
        CefKeyEvent keyEvent = createDeleteKeyEvent(false);

        assertEquals(CefKeyEvent.KEYEVENT_KEYUP, keyEvent.type);
        assertEquals(WINDOWS_VK_DELETE, keyEvent.windows_key_code);
        assertEquals(
                CefKeyEvent.buildWindowsNativeKeyCode(WINDOWS_DELETE_SCAN_CODE, true, true),
                keyEvent.native_key_code
        );
    }

    private CefKeyEvent createDeleteKeyEvent(boolean pressed) {
        return GrapheneKeyboardInputBridge.createRawKeyEvent(
                resolver,
                GLFW.GLFW_KEY_DELETE,
                GLFW_WINDOWS_DELETE_SCAN_CODE,
                0,
                pressed,
                KeyEvent.CHAR_UNDEFINED,
                false
        );
    }
}
