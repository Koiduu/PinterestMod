package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The signed-in Pinterest account. Logging in happens in the embedded browser on pinterest.com itself;
 * afterwards Chromium's session cookies are copied into {@link PinterestApi} so the native screens act
 * as that user, and stored so the login survives restarts.
 */
public final class PinterestAccount {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-account.json");
    private static final String COOKIE_DOMAIN = ".pinterest.com";
    /** The cookies Pinterest's own web app needs to treat a request as signed in. */
    private static final String[] SESSION_COOKIES = {"_pinterest_sess", "_auth", "_b", "csrftoken", "sessionFunnelEventLogged"};

    private static Map<String, String> cookies = new LinkedHashMap<>();
    private static String username = "";

    private PinterestAccount() {
    }

    public static boolean isSignedIn() {
        return cookies.containsKey("_pinterest_sess") && "1".equals(cookies.get("_auth"));
    }

    public static String username() {
        return username;
    }

    /** Loads the stored session at startup and hands it to {@link PinterestApi}. */
    public static void restore() {
        if (!Files.isRegularFile(PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Stored stored = GSON.fromJson(reader, Stored.class);
            if (stored != null && stored.cookies != null) {
                cookies = new LinkedHashMap<>(stored.cookies);
                username = stored.username == null ? "" : stored.username;
                PinterestApi.setSessionCookies(cookies);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the stored Pinterest session", e);
        }
    }

    /**
     * Copies the current Chromium cookies for pinterest.com into the API client. Called after the player
     * closes the login browser.
     *
     * @return the signed-in username once it has been confirmed, or an empty string if not signed in
     */
    public static CompletableFuture<String> importFromBrowser() {
        Map<String, String> collected = new LinkedHashMap<>();
        try {
            CefCookieManager.getGlobalManager().visitUrlCookies(
                    "https://www.pinterest.com/", true,
                    (cookie, count, total, delete) -> {
                        for (String wanted : SESSION_COOKIES) {
                            if (wanted.equals(cookie.name)) {
                                collected.put(cookie.name, cookie.value);
                            }
                        }
                        return true;
                    });
        } catch (Throwable e) {
            PinSpoClient.LOGGER.warn("Could not read cookies from the embedded browser", e);
            return CompletableFuture.completedFuture("");
        }
        // visitUrlCookies is asynchronous on the CEF IO thread, so give it a moment to deliver.
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(700L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!collected.containsKey("_pinterest_sess")) {
                return "";
            }
            cookies = new LinkedHashMap<>(collected);
            PinterestApi.setSessionCookies(cookies);
            username = fetchUsername();
            save();
            return username;
        });
    }

    /**
     * Signs in from a session copied out of the player's own browser: either a full cookie header
     * ({@code _pinterest_sess=...; csrftoken=...}) or the bare {@code _pinterest_sess} value.
     *
     * @return the confirmed username, or an empty string if Pinterest rejected the session
     */
    public static CompletableFuture<String> signInWithCookies(String raw) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String part : raw.trim().split(";")) {
            String piece = part.trim();
            int equals = piece.indexOf('=');
            if (equals > 0) {
                parsed.put(piece.substring(0, equals).trim(), piece.substring(equals + 1).trim());
            } else if (!piece.isEmpty() && parsed.isEmpty()) {
                parsed.put("_pinterest_sess", piece);
            }
        }
        if (!parsed.containsKey("_pinterest_sess")) {
            return CompletableFuture.completedFuture("");
        }
        parsed.putIfAbsent("_auth", "1");
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> previous = cookies;
            cookies = parsed;
            PinterestApi.setSessionCookies(cookies);
            username = fetchUsername();
            if (username.isEmpty()) {
                cookies = previous;
                PinterestApi.setSessionCookies(cookies);
                return "";
            }
            save();
            return username;
        });
    }

    /**
     * Signs in with an email/username and password. Pinterest frequently answers with a bot check, so a
     * failure here is expected rather than exceptional; the browser flow is the fallback.
     *
     * @return the confirmed username, or an empty string when the login was not accepted
     */
    public static CompletableFuture<String> signInWithPassword(String emailOrUsername, String password) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> previous = cookies;
            Map<String, String> session = PinterestApi.logIn(emailOrUsername, password);
            if (!session.containsKey("_pinterest_sess")) {
                PinterestApi.setSessionCookies(previous);
                return "";
            }
            Map<String, String> wanted = new LinkedHashMap<>();
            for (String name : SESSION_COOKIES) {
                String value = session.get(name);
                if (value != null) {
                    wanted.put(name, value);
                }
            }
            cookies = wanted;
            PinterestApi.setSessionCookies(cookies);
            username = fetchUsername();
            if (username.isEmpty()) {
                cookies = previous;
                PinterestApi.setSessionCookies(cookies);
                return "";
            }
            save();
            return username;
        });
    }

    public static void signOut() {
        cookies = new LinkedHashMap<>();
        username = "";
        PinterestApi.setSessionCookies(cookies);
        try {
            CefCookieManager.getGlobalManager().deleteCookies("https://www.pinterest.com/", "");
        } catch (Throwable e) {
            PinSpoClient.LOGGER.debug("Could not clear browser cookies", e);
        }
        try {
            Files.deleteIfExists(PATH);
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not delete the stored Pinterest session", e);
        }
    }

    private static String fetchUsername() {
        JsonObject user = PinterestApi.currentUser();
        if (user == null) {
            return "";
        }
        String name = user.has("username") ? user.get("username").getAsString() : "";
        return name.isEmpty() && user.has("full_name") ? user.get("full_name").getAsString() : name;
    }

    private static void save() {
        Stored stored = new Stored();
        stored.cookies = cookies;
        stored.username = username;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not store the Pinterest session", e);
        }
    }

    public static String cookieDomain() {
        return COOKIE_DOMAIN;
    }

    private static final class Stored {
        @Nullable
        Map<String, String> cookies;
        @Nullable
        String username;
    }
}
