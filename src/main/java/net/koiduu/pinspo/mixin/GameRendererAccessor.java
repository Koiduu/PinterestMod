package net.koiduu.pinspo.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Mutable
    @Accessor("mainRenderTarget")
    void pinspo$setMainRenderTarget(RenderTarget target);

    @Accessor("resourcePool")
    CrossFrameResourcePool pinspo$resourcePool();

    @Mutable
    @Accessor("resourcePool")
    void pinspo$setResourcePool(CrossFrameResourcePool pool);

    @Invoker("extractCamera")
    void pinspo$extractCamera(DeltaTracker deltaTracker, float partialTick);
}
