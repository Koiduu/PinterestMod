package net.koiduu.pinspo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the same JSON search endpoint pinterest.com's own web app uses, so pins can be browsed in a
 * native Minecraft screen instead of an embedded Chromium instance. No login is required for search.
 */
public final class PinterestApi {

    private static final String SEARCH_ENDPOINT = "https://www.pinterest.com/resource/BaseSearchResource/get/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";
    private static final int PAGE_SIZE = 25;

    @Nullable
    private static HttpClient httpClient;
    @Nullable
    private static CookieManager cookies;

    private PinterestApi() {
    }

    /** One page of search results plus the bookmark needed to request the next one. */
    public record Page(List<Pin> pins, @Nullable String bookmark) {
    }

    /**
     * @param thumbnailUrl small image used in the grid
     * @param imageUrl     full-resolution image used for the pinned overlay
     */
    public record Pin(String id, String title, String thumbnailUrl, String imageUrl, int width, int height) {
    }

    public static CompletableFuture<Page> search(String query, @Nullable String bookmark) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return requestPage(query, bookmark);
            } catch (Exception e) {
                throw new IllegalStateException("Pinterest search failed", e);
            }
        });
    }

    private static Page requestPage(String query, @Nullable String bookmark) throws Exception {
        JsonObject options = new JsonObject();
        options.addProperty("query", query);
        options.addProperty("scope", "pins");
        options.addProperty("page_size", PAGE_SIZE);
        if (bookmark != null) {
            JsonArray bookmarks = new JsonArray();
            bookmarks.add(bookmark);
            options.add("bookmarks", bookmarks);
        }
        JsonObject data = new JsonObject();
        data.add("options", options);
        data.add("context", new JsonObject());

        String searchPath = "/search/pins/?q=" + encode(query);
        URI uri = URI.create(SEARCH_ENDPOINT
                + "?source_url=" + encode(searchPath)
                + "&data=" + encode(data.toString()));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*, q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRFToken", csrfToken())
                .header("X-Pinterest-AppState", "active")
                .header("X-Pinterest-PWS-Handler", "www/search/[scope].js")
                .header("Referer", "https://www.pinterest.com" + searchPath)
                .GET()
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Pinterest search returned HTTP " + response.statusCode());
        }
        return parse(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private static Page parse(JsonObject root) {
        JsonObject resourceResponse = root.getAsJsonObject("resource_response");
        List<Pin> pins = new ArrayList<>();
        JsonElement data = resourceResponse.get("data");
        JsonArray results = data != null && data.isJsonObject()
                ? data.getAsJsonObject().getAsJsonArray("results")
                : new JsonArray();
        for (JsonElement element : results) {
            Pin pin = toPin(element);
            if (pin != null) {
                pins.add(pin);
            }
        }
        JsonElement bookmark = resourceResponse.get("bookmark");
        String nextBookmark = bookmark != null && bookmark.isJsonPrimitive() ? bookmark.getAsString() : null;
        return new Page(pins, "-end-".equals(nextBookmark) ? null : nextBookmark);
    }

    @Nullable
    private static Pin toPin(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement images = object.get("images");
        if (images == null || !images.isJsonObject()) {
            return null;
        }
        JsonObject sizes = images.getAsJsonObject();
        JsonObject thumbnail = firstOf(sizes, "236x", "170x", "474x");
        JsonObject full = firstOf(sizes, "orig", "736x", "474x", "236x");
        if (thumbnail == null || full == null) {
            return null;
        }
        String title = string(object, "grid_title");
        if (title.isEmpty()) {
            title = string(object, "title");
        }
        if (title.isEmpty()) {
            title = string(object, "auto_alt_text");
        }
        return new Pin(
                string(object, "id"),
                title,
                thumbnail.get("url").getAsString(),
                full.get("url").getAsString(),
                thumbnail.get("width").getAsInt(),
                thumbnail.get("height").getAsInt());
    }

    @Nullable
    private static JsonObject firstOf(JsonObject sizes, String... keys) {
        for (String key : keys) {
            JsonElement candidate = sizes.get(key);
            if (candidate != null && candidate.isJsonObject() && candidate.getAsJsonObject().has("url")) {
                return candidate.getAsJsonObject();
            }
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    /**
     * The endpoint rejects requests without a matching {@code csrftoken} cookie/header pair, so a plain
     * page load is used to obtain one.
     */
    private static String csrfToken() throws Exception {
        String token = findCsrfCookie();
        if (token != null) {
            return token;
        }
        client().send(
                HttpRequest.newBuilder(URI.create("https://www.pinterest.com/"))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        token = findCsrfCookie();
        if (token == null) {
            throw new IllegalStateException("Pinterest did not hand out a CSRF token");
        }
        return token;
    }

    @Nullable
    private static String findCsrfCookie() {
        if (cookies == null) {
            return null;
        }
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if (cookie.getName().equals("csrftoken")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static HttpClient client() {
        if (httpClient == null) {
            cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .cookieHandler(cookies)
                    .build();
        }
        return httpClient;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
