package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Turns downloaded bytes into a texture-ready image. stb_image (the decoder Minecraft itself ships) goes
 * first: Java's ImageIO misreads JPEGs that carry an Adobe marker but no JFIF header — common on Pinterest
 * originals — and treats their YCbCr data as RGB, which is what made some pins come out rainbow-coloured.
 * ImageIO stays as the fallback for anything stb cannot open.
 */
public final class ImageDecoder {

    private ImageDecoder() {
    }

    public static NativeImage decode(byte[] bytes) throws IOException {
        NativeImage image = decodeWithStb(bytes);
        if (image != null) {
            return image;
        }
        return decodeWithImageIo(bytes);
    }

    @Nullable
    private static NativeImage decodeWithStb(byte[] bytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            buffer.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(buffer, width, height, channels, 4);
            if (pixels == null) {
                PinSpoClient.LOGGER.debug("stb could not decode image ({}), trying ImageIO",
                        STBImage.stbi_failure_reason());
                return null;
            }
            try {
                int w = width.get(0);
                int h = height.get(0);
                NativeImage image = new NativeImage(w, h, false);
                // Both are tightly packed RGBA bytes, so the pixels copy straight across.
                MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), image.getPointer(), (long) w * h * 4L);
                return image;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static NativeImage decodeWithImageIo(byte[] bytes) throws IOException {
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        if (buffered == null) {
            throw new IOException("Unsupported image format");
        }
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        int[] argb = buffered.getRGB(0, 0, width, height, null, 0, width);
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        PinSpoClient.LOGGER.info("Decoded a {}x{} image with ImageIO", width, height);
        return image;
    }
}
