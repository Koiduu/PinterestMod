package net.koiduu.pinspo.mixin;

import net.koiduu.pinspo.util.Utilities;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Far block entity rendering: chests, signs, banners and heads stay visible further out. */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "tryExtractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean pinspo$farBlockEntities(BlockEntityRenderer renderer, BlockEntity blockEntity, Vec3 cameraPos) {
        if (renderer.shouldRender(blockEntity, cameraPos)) {
            return true;
        }
        float scale = Utilities.blockEntityScale();
        return scale > 1.0F && Vec3.atCenterOf(blockEntity.getBlockPos())
                .closerThan(cameraPos, renderer.getViewDistance() * scale);
    }
}
