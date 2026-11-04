package net.koiduu.pinspo.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.koiduu.pinspo.CameraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The sky keeps the player's screen it was created with. Without this, a camera pass drew its sky straight
 * onto the player's finished frame, painting over everything above the camera's horizon.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Redirect(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget pinspo$cameraTarget(SkyRenderer sky) {
        RenderTarget own = ((SkyRendererAccessor) sky).pinspo$renderTarget();
        return CameraRenderer.isRendering() ? Minecraft.getInstance().gameRenderer.mainRenderTarget() : own;
    }
}
