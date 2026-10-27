package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.CameraRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void pinspo$renderCameraPoints(DeltaTracker deltaTracker, CallbackInfo ci) {
        CameraRenderer.afterLevel((GameRenderer) (Object) this, deltaTracker);
    }
}
