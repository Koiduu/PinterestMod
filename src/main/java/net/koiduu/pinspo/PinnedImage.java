package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single pinned reference image: downloads it off-thread, uploads it as a texture on the render
 * thread, and draws it as one alpha-blended quad per frame.
 */
public final class PinnedImage {

    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(PinSpoClient.MOD_ID, "pinned");
    private static final Pattern SIZE_FOLDER = Pattern.compile("/\\d+x\\d*/");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    @Nullable
    private static HttpClient httpClient;
    @Nullable
    private static DynamicTexture texture;
    private static int imageWidth;
    private static int imageHeight;
    private static boolean downloading;

    private PinnedImage() {
    }

    public static boolean isPinned() {
        return texture != null;
    }

    public static boolean isDownloading() {
        return downloading;
    }

    /** Resolves and downloads {@code url}, replacing any currently pinned image once it arrives. */
    public static void pin(String url) {
        downloading = true;
        CompletableFuture
                .supplyAsync(() -> download(url))
                .whenComplete((image, error) -> {
                    downloading = false;
                    if (error != null || image == null) {
                        if (error != null) {
                            PinSpoClient.LOGGER.warn("Failed to download pinned image {}", url, error);
                        }
                        notifyPlayer("message.pinspo.pin_failed");
                        return;
                    }
                    Minecraft.getInstance().execute(() -> upload(image));
                });
    }

    @Nullable
    private static NativeImage download(String url) {
        String candidate = PinSpoConfig.get().preferOriginalResolution ? toOriginalsUrl(url) : null;
        if (candidate != null) {
            NativeImage original = tryDownload(candidate);
            if (original != null) {
                return original;
            }
        }
        return tryDownload(url);
    }

    @Nullable
    private static NativeImage tryDownload(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                PinSpoClient.LOGGER.warn("Image request for {} returned HTTP {}", url, response.statusCode());
                return null;
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.isEmpty() && !contentType.startsWith("image/")) {
                PinSpoClient.LOGGER.warn("URL {} is not an image (content-type {})", url, contentType);
                return null;
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(response.body())) {
                return NativeImage.read(in);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Image download failed for {}: {}", url, e.toString());
            return null;
        }
    }

    /**
     * Pinterest CDN URLs encode the served size as a path segment ({@code /236x/}, {@code /564x/}, ...),
     * which can usually be swapped for {@code /originals/} to get the full-resolution image.
     */
    @Nullable
    private static String toOriginalsUrl(String url) {
        Matcher matcher = SIZE_FOLDER.matcher(url);
        return matcher.find() ? matcher.replaceFirst("/originals/") : null;
    }

    private static void upload(NativeImage image) {
        clear();
        texture = new DynamicTexture(() -> "PinSpo pinned image", image);
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
    }

    public static void clear() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture.close();
            texture = null;
        }
    }

    public static void render(GuiGraphics guiGraphics) {
        if (texture == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        PinSpoConfig config = PinSpoConfig.get();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int width = Math.max(1, Math.round(screenWidth * config.scale));
        int height = Math.max(1, Math.round(width * (float) imageHeight / imageWidth));
        int offsetX = Math.round(screenWidth * config.offsetX);
        int offsetY = Math.round(screenHeight * config.offsetY);

        int x = switch (config.corner) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - offsetX;
        };
        int y = switch (config.corner) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - offsetY;
        };

        int alpha = Math.clamp(Math.round(config.opacity * 255.0F), 0, 255);
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                width,
                height,
                alpha << 24 | 0xFFFFFF
        );
    }

    private static void notifyPlayer(String translationKey) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(translationKey), false);
            }
        });
    }

    private static HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return httpClient;
    }
}
