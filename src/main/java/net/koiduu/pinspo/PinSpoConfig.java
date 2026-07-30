package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PinSpoConfig {

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo.json");

    private static PinSpoConfig instance;

    public Corner corner = Corner.TOP_RIGHT;
    /** Horizontal offset from the chosen corner, as a fraction of screen width. */
    public float offsetX = 0.02F;
    /** Vertical offset from the chosen corner, as a fraction of screen height. */
    public float offsetY = 0.02F;
    /** Overlay width as a fraction of screen width; height follows the image aspect ratio. */
    public float scale = 0.3F;
    public float opacity = 0.85F;
    public boolean preferOriginalResolution = false;
    /** Currently pinned image URL; re-pinned from the on-disk cache on startup. */
    public String pinnedUrl = "";
    /**
     * Chromium paints off-screen on the CPU, so its cost scales with pixel count: capping the render
     * width and upscaling the result is by far the biggest performance lever.
     */
    public int maxBrowserWidth = 1280;
    /** Chromium off-screen frame rate. Lower is cheaper; 60 is smooth. */
    public int browserFrameRate = 60;
    /** Start Chromium in the background at game launch so the first press of the keybind is instant. */
    public boolean preloadBrowser = true;
    /** Minutes of the browser being unused before it is disposed; 0 disables idle disposal. */
    public int idleDisposeMinutes = 5;

    public static PinSpoConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static PinSpoConfig load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                PinSpoConfig loaded = GSON.fromJson(reader, PinSpoConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (Exception e) {
                PinSpoClient.LOGGER.warn("Failed to read PinSpo config, using defaults", e);
            }
        }
        return new PinSpoConfig();
    }

    public void save() {
        clamp();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Failed to save PinSpo config", e);
        }
    }

    private void clamp() {
        if (corner == null) {
            corner = Corner.TOP_RIGHT;
        }
        if (pinnedUrl == null) {
            pinnedUrl = "";
        }
        offsetX = clamp01(offsetX);
        offsetY = clamp01(offsetY);
        scale = Math.clamp(scale, 0.05F, 1.0F);
        opacity = Math.clamp(opacity, 0.05F, 1.0F);
        idleDisposeMinutes = Math.clamp(idleDisposeMinutes, 0, 60);
        maxBrowserWidth = Math.clamp(maxBrowserWidth, 640, 3840);
        browserFrameRate = Math.clamp(browserFrameRate, 15, 120);
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0.0F, 1.0F);
    }
}
