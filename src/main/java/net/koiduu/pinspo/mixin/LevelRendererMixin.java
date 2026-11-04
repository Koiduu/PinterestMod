package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.CameraRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * The chunk grid is centred on whoever renders. Re-centring it on a camera every other frame shifted the
 * grid away from the player, so their view lost every section above (or below) a height and showed sky.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "repositionCamera", at = @At("HEAD"), cancellable = true)
    private void pinspo$keepGridOnPlayer(CallbackInfo ci) {
        if (CameraRenderer.isRendering()) {
            ci.cancel();
        }
    }
}
