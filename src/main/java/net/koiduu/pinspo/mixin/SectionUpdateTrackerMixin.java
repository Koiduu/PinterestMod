package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.CameraRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.SectionUpdateTracker;

@Mixin(SectionUpdateTracker.class)
public abstract class SectionUpdateTrackerMixin {

    @Inject(method = "repositionCamera", at = @At("HEAD"), cancellable = true)
    private void pinspo$keepTrackerOnPlayer(CallbackInfo ci) {
        if (CameraRenderer.isRendering()) {
            ci.cancel();
        }
    }
}
