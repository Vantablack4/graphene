package tytoo.grapheneui.internal.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tytoo.grapheneui.internal.screen.GrapheneScreenBridge;

@Mixin(Minecraft.class)
@SuppressWarnings({"java:S100", "java:S116"}) // Yes sonar this is a mixin.
public abstract class MinecraftScreenOpenMixin {
    @Shadow
    public Screen screen;

    @Shadow
    public ClientLevel level;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void grapheneui$rememberOpenedOverWorld(Screen nextScreen, CallbackInfo callbackInfo) {
        if (screen == null && level != null && nextScreen instanceof GrapheneScreenBridge screenBridge) {
            screenBridge.graphene$markOpenedOverWorld();
        }
    }
}
