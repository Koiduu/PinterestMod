package net.koiduu.pinspo.mixin;

import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelExtractor.class)
public interface LevelExtractorAccessor {

    @Accessor("prevCamRotX")
    double pinspo$prevCamRotX();

    @Accessor("prevCamRotX")
    void pinspo$setPrevCamRotX(double value);

    @Accessor("prevCamRotY")
    double pinspo$prevCamRotY();

    @Accessor("prevCamRotY")
    void pinspo$setPrevCamRotY(double value);
}
