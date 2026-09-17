package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Decodes downloaded images into a {@link NativeImage}. {@code NativeImage.read} only accepts PNG, while
 * Pinterest serves JPEG, so decoding goes through ImageIO and the pixels are copied across.
 */
public final class ImageDecoder {

    private ImageDecoder() {
    }

    public static NativeImage decode(byte[] bytes) throws IOException {
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
                int pixel = argb[y * width + x];
                image.setPixelABGR(x, y, (pixel & 0xFF00FF00)
                        | ((pixel & 0x00FF0000) >> 16)
                        | ((pixel & 0x000000FF) << 16));
            }
        }
        return image;
    }
}
