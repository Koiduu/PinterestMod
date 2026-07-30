package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Downloads grid thumbnails off-thread and keeps them as textures for as long as the grid is open. */
public final class ThumbnailCache {

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    @Nullable
    private static HttpClient httpClient;

    private ThumbnailCache() {
    }

    /** Returns the thumbnail texture, starting a download the first time a URL is requested. */
    @Nullable
    public static Identifier get(String url) {
        Identifier existing = TEXTURES.get(url);
        if (existing != null || FAILED.contains(url) || !IN_FLIGHT.add(url)) {
            return existing;
        }
        CompletableFuture
                .supplyAsync(() -> download(url))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    IN_FLIGHT.remove(url);
                    if (image == null) {
                        FAILED.add(url);
                        if (error != null) {
                            PinSpoClient.LOGGER.debug("Thumbnail download failed for {}", url, error);
                        }
                        return;
                    }
                    Identifier id = Identifier.fromNamespaceAndPath(
                            PinSpoClient.MOD_ID, "thumbnail/" + hash(url));
                    Minecraft.getInstance().getTextureManager()
                            .register(id, new DynamicTexture(() -> "PinSpo thumbnail", image));
                    TEXTURES.put(url, id);
                }));
        return null;
    }

    @Nullable
    private static NativeImage download(String url) {
        try {
            HttpResponse<byte[]> response = client().send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(15))
                            .header("User-Agent", "PinSpo/1.0")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() / 100 == 2 ? NativeImage.read(response.body()) : null;
        } catch (Exception e) {
            PinSpoClient.LOGGER.debug("Thumbnail download failed for {}: {}", url, e.toString());
            return null;
        }
    }

    /** Releases every thumbnail texture; called when the browse screen closes. */
    public static void clear() {
        Minecraft client = Minecraft.getInstance();
        TEXTURES.values().forEach(id -> client.getTextureManager().release(id));
        TEXTURES.clear();
        FAILED.clear();
    }

    private static String hash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                name.append("%02x".formatted(digest[i]));
            }
            return name.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private static HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return httpClient;
    }
}
