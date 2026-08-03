package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.Window;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Owns the single embedded Chromium browser instance. Chromium is only touched once the player first
 * opens the browser screen; afterwards the instance is kept warm (but not rendered) so re-opening is
 * instant, and is disposed on disconnect, on shutdown, or after an idle timeout.
 */
public final class BrowserHolder {

    public static final String HOME_URL = "https://www.pinterest.com/login";
    public static final Identifier BROWSER_TEXTURE_ID =
            Identifier.fromNamespaceAndPath(PinSpoClient.MOD_ID, "browser");

    private static final WrappedGpuTexture BROWSER_TEXTURE = new WrappedGpuTexture();
    private static boolean textureRegistered;

    @Nullable
    private static CompletableFuture<MCEFApi> apiFuture;
    @Nullable
    private static MCEFBrowser browser;
    private static boolean initFailureReported;
    private static boolean preloadAttempted;
    private static long lastUsedMillis;
    private static boolean visible;

    private BrowserHolder() {
    }

    /** Starts (or re-uses) the asynchronous MCEF initialization. Safe to call from the client thread. */
    public static CompletableFuture<MCEFApi> api() {
        if (apiFuture == null) {
            apiFuture = MCEFApi.getInstanceFuture();
        }
        return apiFuture;
    }

    public static MCEFApi.Initialization.Stage initStage() {
        MCEFApi.Initialization initialization = MCEFApi.initialize();
        return initialization.getStage();
    }

    public static float initPercentage() {
        return MCEFApi.initialize().getPercentage();
    }

    /**
     * Returns the browser, creating it if MCEF has finished initializing, or {@code null} while
     * initialization is still running or if it failed.
     */
    @Nullable
    public static MCEFBrowser browserIfReady(int width, int height) {
        CompletableFuture<MCEFApi> future = api();
        if (!future.isDone()) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            reportInitFailure();
            return null;
        }
        if (browser == null) {
            try {
                browser = future.join().createBrowser(HOME_URL, false);
                browser.resize(Math.max(width, 1), Math.max(height, 1));
                browser.getCefBrowser().setWindowlessFrameRate(PinSpoConfig.get().browserFrameRate);
            } catch (Throwable e) {
                browser = null;
                PinSpoClient.LOGGER.error("Failed to create embedded browser", e);
                reportInitFailure();
                return null;
            }
        }
        markUsed();
        return browser;
    }

    /** Caps Chromium's horizontal render resolution; the frame is upscaled to fill the screen. */
    public static int renderWidth(int framebufferWidth) {
        return Math.min(framebufferWidth, PinSpoConfig.get().maxBrowserWidth);
    }

    public static void markUsed() {
        lastUsedMillis = System.currentTimeMillis();
    }

    /** Registers/updates the browser frame in the texture manager and returns whether a frame exists. */
    public static boolean updateTexture() {
        Minecraft client = Minecraft.getInstance();
        if (!textureRegistered) {
            client.getTextureManager().register(BROWSER_TEXTURE_ID, BROWSER_TEXTURE);
            textureRegistered = true;
        }
        MCEFBrowser current = browser;
        if (current == null) {
            BROWSER_TEXTURE.setFrame(null, null);
            return false;
        }
        BROWSER_TEXTURE.setFrame(current.getTexture(), current.getTextureView());
        return BROWSER_TEXTURE.hasFrame();
    }

    /**
     * Tells Chromium whether its off-screen surface is on screen; while hidden it stops producing
     * frames instead of burning CPU/GPU in the background.
     */
    public static void setVisible(boolean newVisible) {
        visible = newVisible;
        MCEFBrowser current = browser;
        if (current != null) {
            try {
                current.getCefBrowser().setWindowVisibility(newVisible);
            } catch (Throwable e) {
                PinSpoClient.LOGGER.debug("Failed to change browser visibility", e);
            }
        }
        if (newVisible) {
            markUsed();
        }
    }

    public static void tick() {
        PinSpoConfig config = PinSpoConfig.get();
        if (config.preloadBrowser && !preloadAttempted && browser == null && !visible) {
            // Warm Chromium up in the background: MCEF init plus the first Pinterest load takes tens of
            // seconds, and doing it before the keybind is pressed makes opening feel instant.
            CompletableFuture<MCEFApi> future = api();
            if (future.isDone()) {
                preloadAttempted = true;
                if (!future.isCompletedExceptionally()) {
                    Window window = Minecraft.getInstance().getWindow();
                    int renderWidth = renderWidth(
                            Math.round(window.getWidth() * config.browserWindowScale));
                    int renderHeight = Math.max(1,
                            Math.round(window.getHeight() * (float) renderWidth / Math.max(1, window.getWidth())));
                    if (browserIfReady(renderWidth, renderHeight) != null) {
                        setVisible(false);
                    }
                }
            }
        }

        int idleMinutes = config.idleDisposeMinutes;
        if (browser == null || visible || idleMinutes <= 0) {
            return;
        }
        if (System.currentTimeMillis() - lastUsedMillis > idleMinutes * 60_000L) {
            PinSpoClient.LOGGER.info("Disposing idle PinSpo browser after {} minutes", idleMinutes);
            dispose();
        }
    }

    public static void dispose() {
        MCEFBrowser current = browser;
        browser = null;
        visible = false;
        BROWSER_TEXTURE.setFrame(null, null);
        if (current != null) {
            try {
                current.close();
            } catch (Throwable e) {
                PinSpoClient.LOGGER.warn("Failed to close embedded browser", e);
            }
        }
    }

    private static void reportInitFailure() {
        if (initFailureReported) {
            return;
        }
        initFailureReported = true;
        PinSpoClient.LOGGER.error("MCEF failed to initialize; the embedded browser is unavailable");
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.translatable("message.pinspo.browser_unavailable"), false);
        }
    }
}
