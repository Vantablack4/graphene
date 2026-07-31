package tytoo.grapheneui.internal.browser;

import org.lwjgl.glfw.GLFW;
import tytoo.grapheneui.internal.input.GrapheneInputModifierUtil;

public final class GrapheneWebViewShortcutPolicy {
    private GrapheneWebViewShortcutPolicy() {
    }

    public static boolean isZoomShortcut(int keyCode, int modifiers) {
        if (!GrapheneInputModifierUtil.isEditShortcutModifierDown(modifiers)) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_EQUAL,
                 GLFW.GLFW_KEY_MINUS,
                 GLFW.GLFW_KEY_0,
                 GLFW.GLFW_KEY_KP_ADD,
                 GLFW.GLFW_KEY_KP_SUBTRACT,
                 GLFW.GLFW_KEY_KP_0 -> true;
            default -> false;
        };
    }

    public static boolean isAltF4Shortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_F4 && (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }
}
