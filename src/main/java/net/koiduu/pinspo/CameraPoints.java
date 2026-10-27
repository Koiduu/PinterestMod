package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cameras the player planted around their build. Each one keeps the exact eye position and facing it was
 * planted with, and shows a live view in its own little window that can be moved and resized.
 */
public final class CameraPoints {

    public static final int MAX_PER_WORLD = 4;
    public static final float MIN_WINDOW = 0.12F;
    public static final float MAX_WINDOW = 0.9F;
    public static final int MIN_FOV = 5;
    public static final int MAX_FOV = 110;
    private static final int TITLE_HEIGHT = 11;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-cameras.json");

    /** One planted camera and where its window sits, as fractions of the screen. */
    public static final class Point {
        public String name = "";
        public String world = "";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public boolean visible = true;
        /** Field of view in degrees; 0 follows the game's own FOV setting. Lower is more zoomed in. */
        public float fov;
        public float windowX = 0.02F;
        public float windowY = 0.55F;
        /** Window width as a fraction of the screen; the height keeps the screen's own shape. */
        public float windowWidth = 0.26F;

        public Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    /** A window's on-screen box in GUI pixels, including its title strip. */
    public record Box(int x, int y, int width, int height) {

        public int viewTop() {
            return y + TITLE_HEIGHT;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static List<Point> stored;

    private CameraPoints() {
    }

    public static List<Point> here() {
        String world = WallPhotos.worldKey();
        List<Point> points = new ArrayList<>();
        for (Point point : all()) {
            if (point.world.equals(world)) {
                points.add(point);
            }
        }
        return points;
    }

    public static List<Point> visibleHere() {
        List<Point> points = here();
        points.removeIf(point -> !point.visible);
        return points;
    }

    /** Plants a camera at the player's eyes, looking exactly where they are looking. */
    public static void plant(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        List<Point> here = here();
        // Hidden cameras cost nothing, so a full set makes room by dropping the oldest hidden one.
        if (here.size() >= MAX_PER_WORLD) {
            for (Point old : here) {
                if (!old.visible) {
                    remove(old);
                    here.remove(old);
                    break;
                }
            }
        }
        if (here.size() >= MAX_PER_WORLD) {
            tell(Component.translatable("message.pinspo.camera_full", MAX_PER_WORLD));
            return;
        }
        Point point = new Point();
        point.world = WallPhotos.worldKey();
        Vec3 eyes = client.player.getEyePosition();
        point.x = eyes.x;
        point.y = eyes.y;
        point.z = eyes.z;
        point.yaw = client.player.getYRot();
        point.pitch = client.player.getXRot();
        point.name = Component.translatable("camera.pinspo.name", freeNumber(here)).getString();
        // Each new window steps down the left edge so they never land exactly on top of one another.
        point.windowY = 0.08F + 0.22F * here.size();
        all().add(point);
        save();
        PinSpoClient.LOGGER.info("Planted {} at {} {} {} (yaw {}, pitch {})", point.name,
                Math.round(point.x), Math.round(point.y), Math.round(point.z),
                Math.round(point.yaw), Math.round(point.pitch));
        tell(Component.translatable("message.pinspo.camera_planted", point.name,
                PinSpoClient.CAMERA_KEY.getTranslatedKeyMessage()));
    }

    public static void remove(Point point) {
        all().remove(point);
        CameraRenderer.release(point);
        save();
    }

    public static void toggle(Point point) {
        point.visible = !point.visible;
        if (!point.visible) {
            CameraRenderer.release(point);
        }
        save();
    }

    /** Zooms by changing the field of view; {@code steps} > 0 zooms in. */
    public static void zoom(Point point, double steps) {
        float base = point.fov > 0.0F ? point.fov : Minecraft.getInstance().options.fov().get();
        // Multiplicative, so each notch feels the same whether the view is wide or already tight.
        float next = (float) (base * Math.pow(0.9D, steps));
        point.fov = Math.clamp(Math.round(next), MIN_FOV, MAX_FOV);
    }

    private static int freeNumber(List<Point> here) {
        for (int number = 1; ; number++) {
            String name = Component.translatable("camera.pinspo.name", number).getString();
            if (here.stream().noneMatch(point -> point.name.equals(name))) {
                return number;
            }
        }
    }

    /** Where a camera's window is, clamped so it can never be dragged fully off-screen. */
    public static Box box(Point point, int screenWidth, int screenHeight) {
        int width = Math.max(48, Math.round(screenWidth * Math.clamp(point.windowWidth, MIN_WINDOW, MAX_WINDOW)));
        int viewHeight = Math.max(27, Math.round((float) width * screenHeight / Math.max(1, screenWidth)));
        int height = viewHeight + TITLE_HEIGHT;
        int x = Math.clamp(Math.round(point.windowX * screenWidth), 0, Math.max(0, screenWidth - width));
        int y = Math.clamp(Math.round(point.windowY * screenHeight), 0, Math.max(0, screenHeight - height));
        return new Box(x, y, width, height);
    }

    /** Draws every visible camera window; part of the HUD so it shows while playing. */
    public static void render(GuiGraphicsExtractor guiGraphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Font font = client.font;
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        for (Point point : visibleHere()) {
            Box box = box(point, screenWidth, screenHeight);
            PinTheme.roundedRect(guiGraphics, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2,
                    PinTheme.BORDER);
            guiGraphics.fill(box.x(), box.y(), box.x() + box.width(), box.viewTop(), PinTheme.BAR);
            guiGraphics.fill(box.x() + 3, box.y() + 4, box.x() + 6, box.y() + 7, PinTheme.ACCENT);
            String fovLabel = point.fov > 0.0F ? Math.round(point.fov) + "\u00b0" : "";
            int fovWidth = fovLabel.isEmpty() ? 0 : font.width(fovLabel) + 14;
            guiGraphics.text(font, font.plainSubstrByWidth(point.name, box.width() - 12 - fovWidth),
                    box.x() + 9, box.y() + 2, PinTheme.TEXT_SOFT, false);
            if (!fovLabel.isEmpty()) {
                guiGraphics.text(font, fovLabel, box.x() + box.width() - fovWidth, box.y() + 2,
                        PinTheme.TEXT_MUTED, false);
            }
            int viewHeight = box.height() - (box.viewTop() - box.y());
            if (!CameraRenderer.draw(guiGraphics, point, box.x(), box.viewTop(), box.width(), viewHeight)) {
                guiGraphics.fill(box.x(), box.viewTop(), box.x() + box.width(), box.y() + box.height(),
                        PinTheme.BACKDROP);
                guiGraphics.centeredText(font, Component.translatable("camera.pinspo.starting"),
                        box.x() + box.width() / 2, box.viewTop() + viewHeight / 2 - 4, PinTheme.TEXT_MUTED);
            }
        }
    }

    private static void tell(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }

    private static List<Point> all() {
        if (stored == null) {
            stored = new ArrayList<>();
            if (Files.isRegularFile(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    Point[] loaded = GSON.fromJson(reader, Point[].class);
                    if (loaded != null) {
                        for (Point point : loaded) {
                            if (point != null && point.world != null && point.name != null) {
                                stored.add(point);
                            }
                        }
                    }
                } catch (Exception e) {
                    PinSpoClient.LOGGER.warn("Could not read camera points", e);
                }
            }
        }
        return stored;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(all(), writer);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not save camera points", e);
        }
    }
}
