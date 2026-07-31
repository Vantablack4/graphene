package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneWebViewShortcutPolicyTest {
    private static final int EDIT_SHORTCUT_MODIFIERS = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER;

    @Test
    void recognizesKeyboardZoomShortcuts() {
        assertTrue(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_EQUAL, EDIT_SHORTCUT_MODIFIERS));
        assertTrue(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_MINUS, EDIT_SHORTCUT_MODIFIERS));
        assertTrue(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_0, EDIT_SHORTCUT_MODIFIERS));
        assertTrue(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_KP_ADD, EDIT_SHORTCUT_MODIFIERS));
        assertTrue(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_KP_SUBTRACT, EDIT_SHORTCUT_MODIFIERS));
    }

    @Test
    void leavesOrdinaryKeyboardInputAvailable() {
        assertFalse(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_EQUAL, 0));
        assertFalse(GrapheneWebViewShortcutPolicy.isZoomShortcut(GLFW.GLFW_KEY_A, EDIT_SHORTCUT_MODIFIERS));
    }

    @Test
    void recognizesAltF4OnlyWithAltModifier() {
        assertTrue(GrapheneWebViewShortcutPolicy.isAltF4Shortcut(GLFW.GLFW_KEY_F4, GLFW.GLFW_MOD_ALT));
        assertFalse(GrapheneWebViewShortcutPolicy.isAltF4Shortcut(GLFW.GLFW_KEY_F4, 0));
        assertFalse(GrapheneWebViewShortcutPolicy.isAltF4Shortcut(GLFW.GLFW_KEY_F5, GLFW.GLFW_MOD_ALT));
    }
}
