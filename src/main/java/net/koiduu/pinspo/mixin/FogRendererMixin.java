package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.util.Utilities;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Utilities tab's fog switches, and the longer view range for camera windows. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Shadow
    protected abstract FogType getFogType(Camera camera);

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void pinspo$adjustFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
                                  float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        Utilities.adjustFog(cir.getReturnValue(), getFogType(camera), camera.entity());
    }
}
