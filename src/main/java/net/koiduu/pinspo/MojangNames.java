package net.koiduu.pinspo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks a Minecraft name against Mojang's account API, so a friend request cannot quietly go out to a
 * player who does not exist (usually a typo).
 */
public final class MojangNames {

    private static final String PROFILE_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int MAX_CACHED = 128;

    /** Answers already given, so retyping a name costs nothing. */
    private static final Map<String, Boolean> known = new ConcurrentHashMap<>();

    private static HttpClient httpClient;

    private MojangNames() {
    }

    /**
     * Whether Mojang knows this name. A lookup that cannot be made — offline, rate limited — answers
     * {@code true}, so a broken connection never blocks a request the player wanted to send.
     */
    public static CompletableFuture<Boolean> exists(String name) {
        if (!PinSecurity.isPlayerName(name)) {
            return CompletableFuture.completedFuture(false);
        }
        String key = name.toLowerCase();
        Boolean cached = known.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<Void> response = client().send(
                        HttpRequest.newBuilder(URI.create(PROFILE_ENDPOINT + name))
                                .timeout(Duration.ofSeconds(8))
                                .header("User-Agent", "PinSpo/1.0")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    remember(key, response.statusCode() == 200);
                    return response.statusCode() == 200;
                }
                if (response.statusCode() == 404) {
                    remember(key, false);
                    return false;
                }
                PinSpoClient.LOGGER.warn("Mojang name lookup returned HTTP {}", response.statusCode());
                return true;
            } catch (Exception e) {
                PinSpoClient.LOGGER.warn("Could not check a Minecraft name with Mojang", e);
                return true;
            }
        });
    }

    private static void remember(String key, boolean value) {
        if (known.size() >= MAX_CACHED) {
            known.clear();
        }
        known.put(key, value);
    }

    private static synchronized HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return httpClient;
    }
}
