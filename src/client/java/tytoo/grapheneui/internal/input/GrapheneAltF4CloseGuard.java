package tytoo.grapheneui.internal.input;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget;
import tytoo.grapheneui.internal.mc.McClient;
import tytoo.grapheneui.internal.screen.GrapheneScreenBridge;

public final class GrapheneAltF4CloseGuard {
    private GrapheneAltF4CloseGuard() {
    }

    public static boolean shouldBlock(long windowHandle) {
        if (!isAltF4Down(windowHandle)) {
            return false;
        }

        try {
            Screen screen = McClient.currentScreen();
            if (!(screen instanceof GrapheneScreenBridge screenBridge)) {
                return false;
            }

            for (GrapheneWebViewWidget webViewWidget : screenBridge.graphene$webViewWidgets()) {
                if (!webViewWidget.getSurface().allowsAltF4Close()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // Preserve normal close behavior if the current screen is unavailable.
            return false;
        }

        return false;
    }

    private static boolean isAltF4Down(long windowHandle) {
        return isKeyDown(windowHandle, GLFW.GLFW_KEY_F4)
                && (isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT)
                    || isKeyDown(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT));
    }

    private static boolean isKeyDown(long windowHandle, int keyCode) {
        int keyState = GLFW.glfwGetKey(windowHandle, keyCode);
        return keyState == GLFW.GLFW_PRESS || keyState == GLFW.GLFW_REPEAT;
    }
}
