package net.koiduu.pinspo.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.koiduu.pinspo.PinSpoClient;
import net.koiduu.pinspo.WallPhotos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
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
import java.util.List;

/**
 * White guide lines you draw in the world. Each press of the key drops a point where you look and joins it to
 * the last one; aiming near an existing point snaps onto it, so lines can branch off or close into shapes.
 * Points snap to the half-block grid: block corners, edge middles and face centres.
 */
public final class Holograms {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-holograms.json");
    private static final double REACH = 96.0;
    private static final double SNAP = 0.4;
    private static final double AIR_DISTANCE = 5.0;
    private static final int MAX_LINES = 2000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PREVIEW = 0x99FFFFFF;

    public static final class Line {
        public String world = "";
        public double x1;
        public double y1;
        public double z1;
        public double x2;
        public double y2;
        public double z2;

        Vec3 a() {
            return new Vec3(x1, y1, z1);
        }

        Vec3 b() {
            return new Vec3(x2, y2, z2);
        }
    }

    private static List<Line> stored;
    /** The end of the line being drawn; the next point joins onto it. Null when no line is in progress. */
    @Nullable
    private static Vec3 chain;
    private static int hudTicks;

    private Holograms() {
    }

    public static boolean drawing() {
        return chain != null;
    }

    /** Drops a point where the player looks and joins it to the previous one. */
    public static void place(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        Vec3 point = aim(client);
        if (chain == null) {
            chain = point;
            tell(Component.translatable("message.pinspo.holo_start", PinSpoClient.HOLOGRAM_KEY.getTranslatedKeyMessage()));
            return;
        }
        if (point.distanceToSqr(chain) < 1.0E-4) {
            return;
        }
        List<Line> lines = all();
        if (lines.size() >= MAX_LINES) {
            tell(Component.translatable("message.pinspo.holo_full", MAX_LINES));
            return;
        }
        Line line = new Line();
        line.world = WallPhotos.worldKey();
        line.x1 = chain.x;
        line.y1 = chain.y;
        line.z1 = chain.z;
        line.x2 = point.x;
        line.y2 = point.y;
        line.z2 = point.z;
        lines.add(line);
        save();
        PinSpoClient.LOGGER.info("Hologram line {} -> {} ({} blocks)", chain, point, length(chain, point));
        chain = point;
    }

    /** Stops the current line, so the next point starts a fresh one. */
    public static void finish() {
        if (chain != null) {
            chain = null;
            tell(Component.translatable("message.pinspo.holo_done"));
        }
    }

    public static int countHere() {
        String world = WallPhotos.worldKey();
        return (int) all().stream().filter(line -> line.world.equals(world)).count();
    }

    public static void undo() {
        String world = WallPhotos.worldKey();
        List<Line> lines = all();
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).world.equals(world)) {
                Line removed = lines.remove(i);
                chain = chain != null && chain.distanceToSqr(removed.b()) < 1.0E-4 ? removed.a() : chain;
                save();
                return;
            }
        }
    }

    public static void clearHere() {
        String world = WallPhotos.worldKey();
        all().removeIf(line -> line.world.equals(world));
        chain = null;
        save();
    }

    public static void onDisconnect() {
        chain = null;
    }

    /** Where a point would go: the snapped spot under the crosshair, or an existing point close to it. */
    private static Vec3 aim(Minecraft client) {
        HitResult hit = client.player.pick(REACH, 1.0F, false);
        Vec3 spot;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            spot = block.getLocation();
        } else {
            spot = client.player.getEyePosition().add(client.player.getViewVector(1.0F).scale(AIR_DISTANCE));
        }
        Vec3 snapped = new Vec3(half(spot.x), half(spot.y), half(spot.z));
        Vec3 best = null;
        double bestDistance = SNAP * SNAP;
        String world = WallPhotos.worldKey();
        for (Line line : all()) {
            if (!line.world.equals(world)) {
                continue;
            }
            for (Vec3 end : new Vec3[] {line.a(), line.b()}) {
                double distance = end.distanceToSqr(spot);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = end;
                }
            }
        }
        return best != null ? best : snapped;
    }

    private static double half(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private static double length(Vec3 a, Vec3 b) {
        return Math.round(a.distanceTo(b) * 10.0) / 10.0;
    }

    /** While a line is in progress the action bar shows how long the next segment would be. */
    public static void tick(Minecraft client) {
        if (chain == null || client.player == null || client.gui.screen() != null || ++hudTicks < 4) {
            return;
        }
        hudTicks = 0;
        Vec3 next = aim(client);
        Vec3 d = next.subtract(chain);
        tell(Component.translatable("message.pinspo.holo_length", length(chain, next),
                Math.abs(d.x), Math.abs(d.y), Math.abs(d.z),
                PinSpoClient.HOLOGRAM_KEY.getTranslatedKeyMessage()));
    }

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!Utilities.get().holograms || client.level == null || client.player == null) {
            return;
        }
        String world = WallPhotos.worldKey();
        List<Line> here = all().stream().filter(line -> line.world.equals(world)).toList();
        if (here.isEmpty() && chain == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Vec3 next = chain != null && client.gui.screen() == null ? aim(client) : null;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(),
                (pose, buffer) -> {
                    for (Line line : here) {
                        line(buffer, pose, camera, line.a(), line.b(), WHITE, 2.5F);
                        cross(buffer, pose, camera, line.a(), WHITE);
                        cross(buffer, pose, camera, line.b(), WHITE);
                    }
                    if (chain != null) {
                        cross(buffer, pose, camera, chain, WHITE);
                        if (next != null) {
                            line(buffer, pose, camera, chain, next, PREVIEW, 1.5F);
                            cross(buffer, pose, camera, next, PREVIEW);
                        }
                    }
                });
    }

    private static void cross(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3 at, int colour) {
        double s = 0.12;
        line(buffer, pose, camera, at.add(-s, 0, 0), at.add(s, 0, 0), colour, 2.0F);
        line(buffer, pose, camera, at.add(0, -s, 0), at.add(0, s, 0), colour, 2.0F);
        line(buffer, pose, camera, at.add(0, 0, -s), at.add(0, 0, s), colour, 2.0F);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3 a, Vec3 b,
                             int colour, float width) {
        float x0 = (float) (a.x - camera.x);
        float y0 = (float) (a.y - camera.y);
        float z0 = (float) (a.z - camera.z);
        float x1 = (float) (b.x - camera.x);
        float y1 = (float) (b.y - camera.y);
        float z1 = (float) (b.z - camera.z);
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-5F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x0, y0, z0).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, x1, y1, z1).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private static List<Line> all() {
        if (stored == null) {
            stored = new ArrayList<>();
            if (Files.isRegularFile(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    List<Line> loaded = GSON.fromJson(reader, new TypeToken<List<Line>>() { }.getType());
                    if (loaded != null) {
                        loaded.removeIf(line -> line == null || line.world == null);
                        stored.addAll(loaded);
                    }
                } catch (Exception e) {
                    PinSpoClient.LOGGER.warn("Could not read hologram lines", e);
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
            PinSpoClient.LOGGER.warn("Could not save hologram lines", e);
        }
    }

    private static void tell(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }
}
