package tytoo.grapheneui.internal.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tytoo.grapheneui.internal.mc.McClient;
import tytoo.grapheneui.internal.screen.GrapheneStartupEscape;

@Mixin(KeyboardHandler.class)
@SuppressWarnings({"java:S100", "java:S116"}) // Yes sonar this is a mixin.
public abstract class KeyboardHandlerMixin {
    @Inject(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"
            ),
            cancellable = true
    )
    private void grapheneui$closeStartingWebViewScreen(long handle, int action, KeyEvent event, CallbackInfo callbackInfo) {
        Screen screen = McClient.currentScreen();
        if (action == 1 && screen != null && GrapheneStartupEscape.closeIfStarting(screen, event)) {
            callbackInfo.cancel();
        }
    }
}
