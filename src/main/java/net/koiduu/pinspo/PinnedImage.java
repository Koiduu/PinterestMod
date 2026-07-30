package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final Pattern PINIMG_URL = Pattern.compile("https://i\\.pinimg\\.com/[^\"'\\s\\\\]+\\.(?:jpg|jpeg|png|webp|gif)");
    private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("pinspo/images");
    /** Downscale before upload: reference overlays never need more, and huge pins cost VRAM and stalls. */
    private static final int MAX_DIMENSION = 1024;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
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
        PinSpoConfig config = PinSpoConfig.get();
        config.pinnedUrl = url;
        config.save();
        load(url);
    }

    /** Re-pins the image saved in the config, served from the on-disk cache when possible. */
    public static void restore() {
        String url = PinSpoConfig.get().pinnedUrl;
        if (!url.isEmpty()) {
            load(url);
        }
    }

    private static void load(String url) {
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
        Path cacheFile = CACHE_DIR.resolve(cacheName(url));
        if (Files.isRegularFile(cacheFile)) {
            try {
                return downscale(NativeImage.read(Files.readAllBytes(cacheFile)));
            } catch (Exception e) {
                PinSpoClient.LOGGER.warn("Discarding unreadable cached image {}", cacheFile, e);
                try {
                    Files.deleteIfExists(cacheFile);
                } catch (IOException ignored) {
                }
            }
        }

        String imageUrl = isPinPageUrl(url) ? scrapeImageUrl(url) : url;
        if (imageUrl == null) {
            return null;
        }

        byte[] bytes = null;
        String originals = PinSpoConfig.get().preferOriginalResolution ? toOriginalsUrl(imageUrl) : null;
        if (originals != null) {
            bytes = fetch(originals);
        }
        if (bytes == null) {
            bytes = fetch(imageUrl);
        }
        if (bytes == null) {
            return null;
        }

        try {
            Files.createDirectories(CACHE_DIR);
            Files.write(cacheFile, bytes);
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not cache pinned image", e);
        }
        try {
            return downscale(NativeImage.read(bytes));
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not decode image {}", imageUrl, e);
            return null;
        }
    }

    /**
     * Shrinks oversized images on the calling (worker) thread, so the render thread only ever does the
     * texture upload.
     */
    private static NativeImage downscale(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return image;
        }
        float factor = Math.min((float) MAX_DIMENSION / width, (float) MAX_DIMENSION / height);
        NativeImage resized = new NativeImage(
                Math.max(1, Math.round(width * factor)),
                Math.max(1, Math.round(height * factor)),
                false);
        image.resizeSubRectTo(0, 0, width, height, resized);
        image.close();
        return resized;
    }

    private static boolean isPinPageUrl(String url) {
        return url.contains("pinterest.") || url.contains("pin.it/");
    }

    /** Falls back to scraping the pin page for the highest-resolution {@code i.pinimg.com} URL it lists. */
    @Nullable
    private static String scrapeImageUrl(String pageUrl) {
        byte[] page = fetch(pageUrl);
        if (page == null) {
            return null;
        }
        String html = new String(page, StandardCharsets.UTF_8);
        String best = null;
        int bestSize = -1;
        Matcher matcher = PINIMG_URL.matcher(html);
        while (matcher.find()) {
            String candidate = matcher.group();
            int size = candidate.contains("/originals/") ? Integer.MAX_VALUE : sizeOf(candidate);
            if (size > bestSize) {
                bestSize = size;
                best = candidate;
            }
        }
        return best;
    }

    private static int sizeOf(String url) {
        Matcher matcher = SIZE_FOLDER.matcher(url);
        if (!matcher.find()) {
            return 0;
        }
        String segment = matcher.group();
        try {
            return Integer.parseInt(segment.substring(1, segment.indexOf('x')));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String cacheName(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                name.append("%02x".formatted(hash[i]));
            }
            return name + ".img";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    private static byte[] fetch(String url) {
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
            byte[] body = response.body();
            if (body.length > MAX_BYTES) {
                PinSpoClient.LOGGER.warn("Refusing {}: {} bytes is too large", url, body.length);
                return null;
            }
            return body;
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Download failed for {}: {}", url, e.toString());
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

    /** Removes the overlay and forgets the pin, but keeps the cached bytes on disk. */
    public static void unpin() {
        clear();
        PinSpoConfig config = PinSpoConfig.get();
        config.pinnedUrl = "";
        config.save();
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
        // Contain-fit inside a box that is `scale` of both screen dimensions, so the overlay keeps the
        // same visual size on any window aspect ratio and tall pins never run off-screen.
        float fit = Math.min(
                screenWidth * config.scale / imageWidth,
                screenHeight * config.scale / imageHeight);
        int width = Math.max(1, Math.round(imageWidth * fit));
        int height = Math.max(1, Math.round(imageHeight * fit));
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
