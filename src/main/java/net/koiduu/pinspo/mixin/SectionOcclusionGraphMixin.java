package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.CameraRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chunk graph belongs to the player's view; camera passes only filter it by their own frustum. */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {

    @Inject(method = {"update", "invalidateIfNeeded"}, at = @At("HEAD"), cancellable = true)
    private void pinspo$keepPlayerGraph(CallbackInfo ci) {
        if (CameraRenderer.isRendering()) {
            ci.cancel();
        }
    }
}
