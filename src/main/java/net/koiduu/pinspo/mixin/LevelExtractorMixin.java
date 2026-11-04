package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.util.Utilities;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Far entity rendering: scales how far away mobs, players and item frames are still drawn. */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    @ModifyArg(method = "extractVisibleEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setViewScale(D)V"))
    private double pinspo$farEntities(double viewScale) {
        return Utilities.entityViewScale(viewScale);
    }
}
