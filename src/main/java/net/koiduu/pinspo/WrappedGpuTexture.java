package net.koiduu.pinspo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes a GPU texture owned by someone else (currently MCEF's off-screen browser frame) through
 * the vanilla {@link net.minecraft.client.renderer.texture.TextureManager}, so it can be drawn with
 * the regular {@code GuiGraphics#blit} calls. Ownership stays with the producer, hence the no-op
 * {@link #close()}.
 */
public class WrappedGpuTexture extends AbstractTexture {

    public void setFrame(@Nullable GpuTexture texture, @Nullable GpuTextureView textureView) {
        this.texture = texture;
        this.textureView = textureView;
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public boolean hasFrame() {
        return textureView != null;
    }

    @Override
    public void close() {
        texture = null;
        textureView = null;
    }
}
