package net.koiduu.pinspo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.browser.CefDevToolsClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves the image URL under a screen position inside the embedded browser.
 * <p>
 * Uses the DevTools protocol ({@code Runtime.evaluate}) rather than the message router, because the
 * router requires access to the shared {@code CefClient} that MCEF Modern owns. The injected script
 * walks a couple of DOM levels around the hit element, since Pinterest wraps pin images in
 * {@code <div>}/{@code <a>} elements, and prefers the largest {@code srcset} candidate.
 */
public final class ImageResolver {

    private static final String SCRIPT = """
            (function() {
              function fromSrcset(srcset) {
                var best = null;
                var bestWidth = -1;
                srcset.split(',').forEach(function(part) {
                  var bits = part.trim().split(/\\s+/);
                  if (!bits[0]) return;
                  var width = bits[1] && bits[1].endsWith('w') ? parseInt(bits[1]) : 0;
                  if (width >= bestWidth) { bestWidth = width; best = bits[0]; }
                });
                return best;
              }
              function urlOf(node) {
                if (!node || !node.tagName) return null;
                var tag = node.tagName.toLowerCase();
                if (tag === 'img') {
                  if (node.srcset) { var picked = fromSrcset(node.srcset); if (picked) return picked; }
                  return node.currentSrc || node.src || null;
                }
                var background = window.getComputedStyle(node).backgroundImage;
                var match = background && background.match(/url\\(["']?(.*?)["']?\\)/);
                return match ? match[1] : null;
              }
              var element = document.elementFromPoint(%d, %d);
              for (var i = 0; element && i < 4; i++) {
                var found = urlOf(element);
                if (found) return found;
                var nested = element.querySelector ? element.querySelector('img') : null;
                if (nested) { var url = urlOf(nested); if (url) return url; }
                element = element.parentElement;
              }
              return null;
            })()
            """;

    private ImageResolver() {
    }

    /** Completes with the image URL under ({@code x}, {@code y}) in browser pixels, or empty. */
    public static CompletableFuture<Optional<String>> resolveAt(MCEFBrowser browser, int x, int y) {
        CefDevToolsClient devTools;
        try {
            devTools = browser.getCefBrowser().getDevToolsClient();
        } catch (Throwable e) {
            PinSpoClient.LOGGER.warn("Could not obtain a DevTools client for image resolution", e);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        JsonObject params = new JsonObject();
        params.addProperty("expression", SCRIPT.formatted(x, y));
        params.addProperty("returnByValue", true);
        params.addProperty("awaitPromise", false);

        return devTools.executeDevToolsMethod("Runtime.evaluate", params.toString())
                .handle((response, error) -> {
                    if (error != null) {
                        PinSpoClient.LOGGER.warn("Runtime.evaluate failed", error);
                        return Optional.empty();
                    }
                    return parseUrl(response);
                });
    }

    private static Optional<String> parseUrl(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject result = root.getAsJsonObject("result");
            if (result == null) {
                return Optional.empty();
            }
            // Runtime.evaluate replies are either {result: {...}} or {result: {result: {...}}}
            // depending on whether the transport unwraps the command envelope.
            JsonObject value = result.has("result") && result.get("result").isJsonObject()
                    ? result.getAsJsonObject("result")
                    : result;
            JsonElement url = value.get("value");
            if (url == null || url.isJsonNull() || !url.isJsonPrimitive()) {
                return Optional.empty();
            }
            String string = url.getAsString();
            return string.startsWith("http") ? Optional.of(string) : Optional.empty();
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not parse DevTools response: {}", response, e);
            return Optional.empty();
        }
    }
}
