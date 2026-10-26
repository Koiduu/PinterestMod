package net.koiduu.pinspo.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPosition")
    void pinspo$setPosition(Vec3 position);

    @Invoker("setRotation")
    void pinspo$setRotation(float yRot, float xRot);

    @Accessor("detached")
    boolean pinspo$detached();

    @Accessor("detached")
    void pinspo$setDetached(boolean detached);

    @Invoker("createProjectionMatrixForCulling")
    Matrix4f pinspo$cullProjection();

    @Invoker("prepareCullFrustum")
    void pinspo$prepareCullFrustum(Matrix4fc viewRotation, Matrix4f projection, Vec3 position);
}
