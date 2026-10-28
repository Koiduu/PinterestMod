package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.CameraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** A camera is a viewpoint, not a player: no arm or held item floating in its window. */
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void pinspo$noHandInCameraViews(CallbackInfo ci) {
        if (CameraRenderer.isRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void pinspo$renderCameraPoints(CallbackInfo ci) {
        CameraRenderer.afterLevel((GameRenderer) (Object) this, Minecraft.getInstance().getDeltaTracker());
    }
}
