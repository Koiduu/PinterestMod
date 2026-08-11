package net.koiduu.pinspo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A tiny loopback-only web server used for one-click sign-in. It serves a setup page holding a
 * bookmarklet; clicking that bookmarklet while on pinterest.com hands the browser's Pinterest cookies
 * back here, which signs the player in without any copying. Works in every browser, unlike reading a
 * browser's cookie store.
 *
 * <p>Safety: it binds to 127.0.0.1 only, so nothing off this computer can reach it; every request has to
 * carry a random per-run token; the request size is capped; it shuts down once a session arrives or after
 * fifteen minutes; and nothing it receives is written to disk before {@link PinterestAccount} has checked
 * it against Pinterest.
 */
public final class PinLoginServer {

    private static final int MAX_BODY = 16 * 1024;
    private static final String HTML = "text/html; charset=utf-8";
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    @Nullable
    private static HttpServer server;
    private static String token = "";
    private static int port;

    private PinLoginServer() {
    }

    /**
     * Starts the server if it is not already running and returns the setup page URL, or {@code null} when
     * no loopback port could be bound.
     *
     * @param onSession called off the render thread with the cookie header the browser sent
     */
    @Nullable
    public static synchronized String start(Consumer<String> onSession) {
        stop();
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        token = HexFormat.of().formatHex(random);
        try {
            HttpServer started = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            started.createContext("/pinspo/setup", exchange -> handleSetup(exchange));
            started.createContext("/pinspo/session", exchange -> handleSession(exchange, onSession));
            started.setExecutor(null);
            started.start();
            server = started;
            port = started.getAddress().getPort();
            // The helper stays up while the player logs in in their browser, but never indefinitely.
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(LIFETIME.toMillis());
                } catch (InterruptedException interrupted) {
                    return;
                }
                if (server == started) {
                    stop();
                }
            }, "PinSpo login helper watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            return "http://127.0.0.1:" + port + "/pinspo/setup?t=" + token;
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not start the PinSpo login helper: {}", e.toString());
            return null;
        }
    }

    public static synchronized void stop() {
        HttpServer running = server;
        server = null;
        token = "";
        if (running != null) {
            running.stop(0);
        }
    }

    public static synchronized boolean isRunning() {
        return server != null;
    }

    private static void handleSetup(HttpExchange exchange) throws java.io.IOException {
        if (!authorised(query(exchange))) {
            respond(exchange, 403, HTML, page("Not authorised", "This link is no longer valid."));
            return;
        }
        respond(exchange, 200, HTML, setupPage());
    }

    /**
     * Receives the browser's Pinterest cookies. The bookmarklet navigates here with them in the query,
     * because pinterest.com's content security policy forbids the page itself from calling localhost.
     */
    private static void handleSession(HttpExchange exchange, Consumer<String> onSession)
            throws java.io.IOException {
        Map<String, String> query = query(exchange);
        if (!authorised(query)) {
            respond(exchange, 403, HTML, page("Not authorised",
                    "Open the Account tab in Minecraft and press the one-click login button again."));
            return;
        }
        String cookieHeader = "GET".equals(exchange.getRequestMethod())
                ? query.getOrDefault("c", "")
                : readBody(exchange);
        if (!cookieHeader.contains("_pinterest_sess")) {
            respond(exchange, 400, HTML, page("Not logged in yet",
                    "Log into pinterest.com first, then click <b>Log into PinSpo</b> again."));
            return;
        }
        respond(exchange, 200, HTML, page("Signing you in...",
                "You can close this tab and go back to Minecraft."));
        onSession.accept(cookieHeader);
    }

    private static boolean authorised(Map<String, String> query) {
        return !token.isEmpty() && token.equals(query.get("t"));
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.length() > MAX_BODY) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(
                        URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    /** A themed page shown in the player's browser, matching the setup page. */
    private static String page(String heading, String body) {
        return ("<!doctype html><html><head><meta charset=\"utf-8\"><title>PinSpo</title>"
                + "<style>body{background:#17181c;color:#eceff4;"
                + "font:16px/1.6 system-ui,sans-serif;display:flex;align-items:center;"
                + "justify-content:center;height:100vh;margin:0;text-align:center}"
                + "h1{color:#e60023;font-size:22px;margin:0 0 8px}p{color:#b6bac2;margin:0}</style>"
                + "</head><body><div><h1>" + heading + "</h1><p>" + body + "</p></div></body></html>");
    }

    private static String readBody(HttpExchange exchange) throws java.io.IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8).trim();
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /** The bookmarklet: reads the Pinterest cookies of the page it runs on and posts them back here. */
    private static String bookmarklet() {
        // A navigation rather than a fetch: pinterest.com's CSP blocks page requests to 127.0.0.1.
        return "javascript:(function(){window.open('http://127.0.0.1:" + port + "/pinspo/session?t="
                + token + "&c='+encodeURIComponent(document.cookie),'_blank')})()";
    }

    private static String setupPage() {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>PinSpo one-click login</title>
                <style>
                  body{background:#17181c;color:#eceff4;font:15px/1.6 system-ui,sans-serif;margin:0;padding:40px}
                  main{max-width:640px;margin:0 auto}
                  h1{color:#e60023;font-size:24px;margin:0 0 4px}
                  p{color:#b6bac2}
                  ol{padding-left:22px}
                  li{margin:14px 0}
                  a.pin{display:inline-block;background:#e60023;color:#fff;text-decoration:none;
                        font-weight:600;padding:10px 18px;border-radius:24px;cursor:grab}
                  code{background:#23252b;padding:2px 6px;border-radius:4px}
                  .note{margin-top:32px;font-size:13px;color:#8a8f99}
                </style></head><body><main>
                <h1>PinSpo one-click login</h1>
                <p>One-time setup. After this, signing into PinSpo is a single click in any browser.</p>
                <ol>
                  <li>Show your bookmarks bar (<code>Ctrl</code>+<code>Shift</code>+<code>B</code>).</li>
                  <li>Drag this button onto it:<br><br>
                      <a class="pin" href="BOOKMARKLET">Log into PinSpo</a></li>
                  <li>Go to <a href="https://www.pinterest.com/" style="color:#e60023">pinterest.com</a>
                      and log in as normal.</li>
                  <li>Click <b>Log into PinSpo</b> in your bookmarks bar, then switch back to Minecraft —
                      you are signed in.</li>
                </ol>
                <p class="note">Your password never touches the mod: the button only hands Minecraft the
                Pinterest session your browser already has. It is sent to 127.0.0.1, your own computer,
                and nowhere else. Keep this Minecraft session open while you do it.</p>
                </main></body></html>
                """.replace("BOOKMARKLET", bookmarklet().replace("&", "&amp;").replace("\"", "&quot;"));
    }
}
