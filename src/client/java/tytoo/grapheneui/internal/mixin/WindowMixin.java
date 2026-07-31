package tytoo.grapheneui.internal.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tytoo.grapheneui.internal.input.GrapheneAltF4CloseGuard;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Inject(method = "setWindowCloseCallback", at = @At("RETURN"))
    private void grapheneui$guardAltF4Close(Runnable closeCallback, CallbackInfo ignoredCallbackInfo) {
        Window window = (Window) (Object) this;
        GLFWWindowCloseCallback previousCallback = GLFW.glfwSetWindowCloseCallback(window.handle(), windowHandle -> {
            if (GrapheneAltF4CloseGuard.shouldBlock(windowHandle)) {
                GLFW.glfwSetWindowShouldClose(windowHandle, false);
                return;
            }

            closeCallback.run();
        });
        if (previousCallback != null) {
            previousCallback.free();
        }
    }
}
