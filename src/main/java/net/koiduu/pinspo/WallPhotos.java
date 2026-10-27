package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference photos stuck onto the world itself: the player's own (placed with the wall key, kept between
 * sessions) and any their PinSpo friends shared with them (kept only while connected).
 */
public final class WallPhotos {

    public static final float MIN_SIZE = 0.5F;
    public static final float MAX_SIZE = 48.0F;
    private static final float DEFAULT_SIZE = 4.0F;
    /** Lifted off the block face so the photo never z-fights with it. */
    private static final double LIFT = 0.012D;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-walls.json");

    private static final String PREFIX = "PinSpo wall ";
    private static final String OFF = PREFIX + "off";
    private static final Pattern MESSAGE = Pattern.compile(Pattern.quote(PREFIX)
            + "([nsewud])([0-3]) (-?\\d{1,9}) (-?\\d{1,6}) (-?\\d{1,9}) (\\d{1,4}) (\\d{1,3}) (-?\\d{1,4}) "
            + "([jpwg]) ((?:[0-9a-f]{1,8}(?: |$)){1,12})");

    /** One photo on a wall. Public fields so Gson can store it as-is. */
    public static final class Photo {
        public String url = "";
        public String world = "";
        public double x;
        public double y;
        public double z;
        public Direction face = Direction.NORTH;
        /** Quarter turns clockwise within the wall's plane. */
        public int turns;
        /** For floors and ceilings: which way the player faced, so the photo's top points away from them. */
        public float yaw;
        /** Width in blocks; the height follows the image. */
        public float size = DEFAULT_SIZE;
        public float opacity = 0.9F;
        public boolean shared;
    }

    private record Texture(Identifier id, int width, int height) {
    }

    private static final Map<String, Texture> TEXTURES = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<String, Photo> FRIENDS = new HashMap<>();

    /** The player's own photos, one per world they placed one in. */
    private static List<Photo> stored;
    private static boolean shareDirty;

    private WallPhotos() {
    }

    // --- the player's own photo -------------------------------------------------------------------------

    @Nullable
    public static Photo mine() {
        String world = worldKey();
        for (Photo photo : all()) {
            if (photo.world.equals(world)) {
                return photo;
            }
        }
        return null;
    }

    /** Puts the photo where the crosshair points, creating it from the pinned image the first time. */
    public static void placeAtCrosshair(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            tell("message.pinspo.wall_look");
            return;
        }
        Photo photo = mine();
        boolean created = photo == null;
        if (created) {
            String url = PinnedImage.currentUrl();
            if (url.isEmpty()) {
                tell("message.pinspo.wall_need_pin");
                return;
            }
            photo = new Photo();
            photo.url = url;
            photo.world = worldKey();
            all().add(photo);
        }
        Direction face = hit.getDirection();
        Vec3 at = hit.getLocation();
        // Snapped to half blocks along the wall so it lines up with the build; the editor fine-tunes it.
        photo.x = face.getStepX() != 0 ? at.x + face.getStepX() * LIFT : Math.round(at.x * 2.0D) / 2.0D;
        photo.y = face.getStepY() != 0 ? at.y + face.getStepY() * LIFT : Math.round(at.y * 2.0D) / 2.0D;
        photo.z = face.getStepZ() != 0 ? at.z + face.getStepZ() * LIFT : Math.round(at.z * 2.0D) / 2.0D;
        photo.face = face;
        photo.yaw = client.player.getYRot();
        save();
        if (photo.shared) {
            shareNow();
        }
        PinSpoClient.LOGGER.info("Wall photo {} at {} {} {} facing {}", created ? "placed" : "moved",
                Math.round(photo.x), Math.round(photo.y), Math.round(photo.z), face);
        Component key = PinSpoClient.WALL_KEY.getTranslatedKeyMessage();
        tell(created ? "message.pinspo.wall_placed" : "message.pinspo.wall_moved", key, key);
    }

    /** Moves the photo by blocks along its own right, up and outward directions. */
    public static void nudge(double right, double up, double out) {
        Photo photo = mine();
        if (photo == null) {
            return;
        }
        Vec3[] axes = axes(photo);
        Vec3 delta = axes[0].scale(right).add(axes[1].scale(up)).add(axes[2].scale(out));
        photo.x += delta.x;
        photo.y += delta.y;
        photo.z += delta.z;
        changed();
    }

    public static void rotate() {
        Photo photo = mine();
        if (photo != null) {
            photo.turns = (photo.turns + 1) & 3;
            changed();
        }
    }

    public static void setSize(float size) {
        Photo photo = mine();
        if (photo != null) {
            photo.size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
            changed();
        }
    }

    public static void setOpacity(float opacity) {
        Photo photo = mine();
        if (photo != null) {
            photo.opacity = Math.clamp(opacity, 0.05F, 1.0F);
            changed();
        }
    }

    /** Swaps the picture on the wall for whatever is pinned now, keeping its place and size. */
    public static boolean usePinnedImage() {
        Photo photo = mine();
        String url = PinnedImage.currentUrl();
        if (photo == null || url.isEmpty()) {
            return false;
        }
        photo.url = url;
        changed();
        return true;
    }

    /**
     * Turns sharing with PinSpo friends on or off. Returns the translation key explaining why it could not
     * be turned on, or {@code null} when it worked.
     */
    @Nullable
    public static String setShared(boolean shared) {
        Photo photo = mine();
        if (photo == null) {
            return null;
        }
        if (shared) {
            if (PinShare.chatImage(photo.url).isEmpty()) {
                return "message.pinspo.wall_not_shareable";
            }
            if (PinFriends.friends().isEmpty()) {
                return "message.pinspo.wall_no_friends";
            }
        }
        photo.shared = shared;
        save();
        if (shared) {
            shareNow();
        } else {
            broadcast(OFF);
        }
        return null;
    }

    public static void remove() {
        Photo photo = mine();
        if (photo == null) {
            return;
        }
        if (photo.shared) {
            broadcast(OFF);
        }
        all().remove(photo);
        save();
    }

    /** Called when an editor closes, so a dozen nudges become one message per friend instead of a dozen. */
    public static void flushShare() {
        if (shareDirty) {
            shareNow();
        }
    }

    private static void changed() {
        save();
        Photo photo = mine();
        shareDirty = photo != null && photo.shared;
    }

    private static void shareNow() {
        shareDirty = false;
        Photo photo = mine();
        if (photo == null || !photo.shared) {
            return;
        }
        String image = PinShare.chatImage(photo.url);
        if (image.isEmpty()) {
            return;
        }
        broadcast(PREFIX + Character.toLowerCase(photo.face.getName().charAt(0)) + photo.turns
                + " " + Math.round(photo.x * 10.0D) + " " + Math.round(photo.y * 10.0D)
                + " " + Math.round(photo.z * 10.0D) + " " + Math.round(photo.size * 10.0F)
                + " " + Math.round(photo.opacity * 100.0F) + " " + Math.round(photo.yaw) + " " + image);
    }

    private static void broadcast(String message) {
        int sent = 0;
        for (String friend : PinFriends.friends()) {
            if (PinChat.sendWall(friend, message)) {
                sent++;
            }
        }
        PinSpoClient.LOGGER.info("Sent wall photo update to {} friend(s)", sent);
    }

    // --- photos friends shared ------------------------------------------------------------------------

    public static boolean isWallMessage(String text) {
        return text.startsWith(PREFIX);
    }

    public static void onFriendMessage(String sender, String text) {
        if (text.equals(OFF)) {
            FRIENDS.remove(sender);
            return;
        }
        Matcher matcher = MESSAGE.matcher(text);
        if (!matcher.find()) {
            PinSpoClient.LOGGER.warn("Ignoring a malformed wall photo from {}", sender);
            return;
        }
        PinterestApi.Pin pin = PinShare.fromChatImage(matcher.group(9), matcher.group(10).trim());
        if (pin == null) {
            return;
        }
        Photo photo = new Photo();
        photo.url = pin.imageUrl();
        photo.face = switch (matcher.group(1)) {
            case "s" -> Direction.SOUTH;
            case "e" -> Direction.EAST;
            case "w" -> Direction.WEST;
            case "u" -> Direction.UP;
            case "d" -> Direction.DOWN;
            default -> Direction.NORTH;
        };
        photo.turns = Integer.parseInt(matcher.group(2));
        photo.x = Long.parseLong(matcher.group(3)) / 10.0D;
        photo.y = Long.parseLong(matcher.group(4)) / 10.0D;
        photo.z = Long.parseLong(matcher.group(5)) / 10.0D;
        photo.size = Math.clamp(Integer.parseInt(matcher.group(6)) / 10.0F, MIN_SIZE, MAX_SIZE);
        photo.opacity = Math.clamp(Integer.parseInt(matcher.group(7)) / 100.0F, 0.05F, 1.0F);
        photo.yaw = Integer.parseInt(matcher.group(8));
        photo.world = worldKey();
        FRIENDS.put(sender, photo);
        PinNotifications.wallShared(sender);
        PinSpoClient.LOGGER.info("{} shared a wall photo with you", sender);
    }

    public static void onDisconnect() {
        FRIENDS.clear();
    }

    // --- drawing -------------------------------------------------------------------------------------

    public static void render(LevelRenderContext context) {
        String world = worldKey();
        Photo own = mine();
        if (own != null) {
            draw(context, own);
        }
        for (Photo photo : FRIENDS.values()) {
            if (photo.world.equals(world)) {
                draw(context, photo);
            }
        }
    }

    private static void draw(LevelRenderContext context, Photo photo) {
        Texture texture = texture(photo.url);
        if (texture == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Vec3[] axes = axes(photo);
        float halfWidth = photo.size / 2.0F;
        float halfHeight = photo.size * texture.height() / texture.width() / 2.0F;
        Vec3 right = axes[0].scale(halfWidth);
        Vec3 up = axes[1].scale(halfHeight);
        Vec3 normal = axes[2];
        int colour = Math.round(photo.opacity * 255.0F) << 24 | 0xFFFFFF;

        PoseStack matrices = context.poseStack();
        matrices.pushPose();
        matrices.translate(photo.x - camera.x, photo.y - camera.y, photo.z - camera.z);
        context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.entityTranslucent(texture.id()),
                (pose, buffer) -> {
                    corner(buffer, pose, up.subtract(right), 0.0F, 0.0F, colour, normal);
                    corner(buffer, pose, up.reverse().subtract(right), 0.0F, 1.0F, colour, normal);
                    corner(buffer, pose, right.subtract(up), 1.0F, 1.0F, colour, normal);
                    corner(buffer, pose, right.add(up), 1.0F, 0.0F, colour, normal);
                });
        matrices.popPose();
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float u, float v,
                               int colour, Vec3 normal) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** The photo's right, up and outward unit vectors in world space. */
    private static Vec3[] axes(Photo photo) {
        Vec3 normal = new Vec3(photo.face.getStepX(), photo.face.getStepY(), photo.face.getStepZ());
        Vec3 up;
        if (photo.face.getAxis().isHorizontal()) {
            up = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            double radians = Math.toRadians(photo.yaw);
            up = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        Vec3 right = up.cross(normal);
        for (int i = 0; i < photo.turns; i++) {
            Vec3 turnedUp = right;
            right = up.reverse();
            up = turnedUp;
        }
        return new Vec3[]{right, up, normal};
    }

    @Nullable
    private static Texture texture(String url) {
        Texture existing = TEXTURES.get(url);
        if (existing != null || FAILED.contains(url) || !LOADING.add(url)) {
            return existing;
        }
        CompletableFuture.supplyAsync(() -> PinnedImage.loadImage(url)).whenComplete((image, error) ->
                Minecraft.getInstance().execute(() -> {
                    LOADING.remove(url);
                    if (image == null) {
                        FAILED.add(url);
                        PinSpoClient.LOGGER.warn("Could not load wall photo {}", url, error);
                        return;
                    }
                    upload(url, image);
                }));
        return null;
    }

    private static void upload(String url, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath(PinSpoClient.MOD_ID,
                "wall/" + Integer.toHexString(url.hashCode()));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "PinSpo wall photo", image));
        TEXTURES.put(url, new Texture(id, Math.max(1, image.getWidth()), Math.max(1, image.getHeight())));
    }

    // --- bookkeeping ---------------------------------------------------------------------------------

    /** Server (or singleplayer world) plus dimension, so a photo only shows where it was placed. */
    public static String worldKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return "";
        }
        String server;
        if (client.getCurrentServer() != null) {
            server = client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        } else if (client.getSingleplayerServer() != null) {
            server = "local:" + client.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            server = "local";
        }
        return server + "|" + client.level.dimension().identifier();
    }

    private static void tell(String key, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(key, args));
        }
    }

    private static List<Photo> all() {
        if (stored == null) {
            stored = new ArrayList<>();
            if (Files.isRegularFile(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    Photo[] loaded = GSON.fromJson(reader, Photo[].class);
                    if (loaded != null) {
                        for (Photo photo : loaded) {
                            if (photo != null && photo.face != null && photo.url != null
                                    && PinSecurity.isPinnableUrl(photo.url)) {
                                photo.size = Math.clamp(photo.size, MIN_SIZE, MAX_SIZE);
                                photo.opacity = Math.clamp(photo.opacity, 0.05F, 1.0F);
                                photo.turns &= 3;
                                stored.add(photo);
                            }
                        }
                    }
                } catch (Exception e) {
                    PinSpoClient.LOGGER.warn("Could not read wall photos", e);
                }
            }
        }
        return stored;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(all(), writer);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not save wall photos", e);
        }
    }
}
