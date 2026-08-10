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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the same JSON search endpoint pinterest.com's own web app uses, so pins can be browsed in a
 * native Minecraft screen instead of an embedded Chromium instance. No login is required for search.
 */
public final class PinterestApi {

    private static final String SEARCH_ENDPOINT = "https://www.pinterest.com/resource/BaseSearchResource/get/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";
    private static final int PAGE_SIZE = 25;
    private static final Pattern APP_VERSION = Pattern.compile("\"app_version\"\\s*:\\s*\"([0-9a-f]{5,12})\"");

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

    /** Installs the signed-in player's Pinterest cookies so later requests act as that account. */
    public static void setSessionCookies(Map<String, String> sessionCookies) {
        client();
        CookieManager manager = cookies;
        if (manager == null) {
            return;
        }
        manager.getCookieStore().removeAll();
        sessionCookies.forEach((name, value) -> {
            HttpCookie cookie = new HttpCookie(name, value);
            cookie.setDomain(PinterestAccount.cookieDomain());
            cookie.setPath("/");
            cookie.setVersion(0);
            manager.getCookieStore().add(URI.create("https://www.pinterest.com"), cookie);
        });
    }

    /** Returns the signed-in user object, or {@code null} when the session is anonymous or invalid. */
    @Nullable
    public static JsonObject currentUser() {
        try {
            JsonObject data = new JsonObject();
            data.add("options", new JsonObject());
            data.add("context", new JsonObject());
            URI uri = URI.create("https://www.pinterest.com/resource/UserSessionResource/get/"
                    + "?source_url=" + encode("/") + "&data=" + encode(data.toString()));
            HttpResponse<String> response = client().send(
                    requestBuilder(uri, "/").build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            JsonElement user = JsonParser.parseString(response.body())
                    .getAsJsonObject().getAsJsonObject("resource_response").get("data");
            return user != null && user.isJsonObject() && user.getAsJsonObject().has("username")
                    ? user.getAsJsonObject()
                    : null;
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the Pinterest user session", e);
            return null;
        }
    }

    /**
     * The outcome of a login attempt: the HTTP status Pinterest answered with (0 when the request never
     * got that far) and the resulting cookies.
     */
    public record LoginResult(int status, Map<String, String> cookies) {

        public boolean accepted() {
            return cookies.containsKey("_pinterest_sess");
        }
    }

    /**
     * Logs in with an email/username and password against the same endpoint pinterest.com's login form
     * posts to. Pinterest often answers with a bot check instead (typically HTTP 429), in which case the
     * browser flow has to be used.
     */
    public static LoginResult logIn(String emailOrUsername, String password) {
        try {
            // The login POST is only accepted with a csrftoken cookie, which the login page hands out.
            HttpResponse<String> loginPage = client().send(
                    HttpRequest.newBuilder(URI.create("https://www.pinterest.com/login/"))
                            .timeout(Duration.ofSeconds(15))
                            .header("User-Agent", USER_AGENT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            String appVersion = appVersion(loginPage.body());

            JsonObject options = new JsonObject();
            options.addProperty("username_or_email", emailOrUsername);
            options.addProperty("password", password);
            JsonObject data = new JsonObject();
            data.add("options", options);
            data.add("context", new JsonObject());

            String body = "source_url=" + encode("/login/") + "&data=" + encode(data.toString());
            HttpRequest request = requestBuilder(
                    URI.create("https://www.pinterest.com/resource/UserSessionResource/create/"), "/login/")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Pinterest-PWS-Handler", "www/login.js")
                    .header("X-Pinterest-Source-Url", "/login/")
                    .header("X-APP-VERSION", appVersion)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                PinSpoClient.LOGGER.warn("Pinterest rejected the login with HTTP {}", response.statusCode());
                return new LoginResult(response.statusCode(), Map.of());
            }
            return new LoginResult(response.statusCode(), sessionCookies());
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Pinterest login failed: {}", e.toString());
            return new LoginResult(0, Map.of());
        }
    }

    /**
     * The build hash pinterest.com's own web app sends as {@code X-APP-VERSION}; the login endpoint is
     * more willing to answer a request that carries the current one.
     */
    private static String appVersion(String loginPageHtml) {
        Matcher matcher = APP_VERSION.matcher(loginPageHtml);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** The cookies currently held for pinterest.com, so a successful login can be persisted. */
    public static Map<String, String> sessionCookies() {
        Map<String, String> collected = new LinkedHashMap<>();
        if (cookies != null) {
            for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
                collected.put(cookie.getName(), cookie.getValue());
            }
        }
        return collected;
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

        HttpResponse<String> response = client().send(
                requestBuilder(uri, searchPath).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Pinterest search returned HTTP " + response.statusCode());
        }
        return parse(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private static HttpRequest.Builder requestBuilder(URI uri, String sourcePath) throws Exception {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*, q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRFToken", csrfToken())
                .header("X-Pinterest-AppState", "active")
                .header("X-Pinterest-PWS-Handler", "www/search/[scope].js")
                .header("Referer", "https://www.pinterest.com" + sourcePath)
                .GET();
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
