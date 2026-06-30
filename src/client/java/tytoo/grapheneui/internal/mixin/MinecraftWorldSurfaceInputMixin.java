package tytoo.grapheneui.internal.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tytoo.grapheneui.internal.world.GrapheneWorldSurfaceManager;

@Mixin(Minecraft.class)
@SuppressWarnings({"java:S100", "java:S116"}) // Yes sonar this is a mixin.
public abstract class MinecraftWorldSurfaceInputMixin {
    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void grapheneui$interactWithWorldSurface(CallbackInfo callbackInfo) {
        if (!GrapheneWorldSurfaceManager.handlePrimaryClickFromCameraRay((Minecraft) (Object) this)) {
            return;
        }

        rightClickDelay = 4;
        callbackInfo.cancel();
    }
}
