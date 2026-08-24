package net.koiduu.pinspo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The composition guide drawn on the build plot itself: finds the floor the player is standing on by
 * flood-filling the blocks it is made of, then draws the guide lines in the world across its bounds.
 */
public final class PlotGrid {

    /** Build Battle plots are far smaller than this; the cap only stops a scan from walking a whole world. */
    private static final int SCAN_RADIUS = 48;
    private static final int MAX_BLOCKS = 24000;
    /** How far below the player's feet the floor may be, so a scan still works while flying or jumping. */
    private static final int MAX_DROP = 8;
    private static final int MIN_SIDE = 3;
    private static final int LINE_COLOUR = 0xB0FFFFFF;
    /** The vertical lattice is dimmer than the floor, so the plot's own surface stays the clearest part. */
    private static final int VERTICAL_COLOUR = 0x70FFFFFF;
    private static final float LINE_WIDTH = 4.0F;
    /** Heights the vertical guide steps through; 0 means "as tall as the plot is wide". */
    private static final int[] HEIGHTS = {0, 8, 12, 16, 24, 32, 48};
    /** Lifted off the surface so the lines never z-fight with the floor blocks. */
    private static final float LIFT = 0.02F;

    /** The floor a scan found: inclusive block bounds and the y of the surface the lines sit on. */
    private record Plot(int minX, int minZ, int maxX, int maxZ, float surfaceY) {

        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }
    }

    /** Ticks between attempts while waiting for a plot, so a failed scan is not retried every tick. */
    private static final int RETRY_TICKS = 20;

    @Nullable
    private static Plot plot;
    /** Hidden without being forgotten, e.g. during Build Battle voting. */
    private static boolean hidden;
    private static int ticksUntilScan;

    private PlotGrid() {
    }

    /**
     * Steps to the next floor guide, keeping the plot already measured. Returns the guide now in use; a
     * null plot afterwards means no floor has been found yet.
     */
    public static PinGuide.Guide cycle() {
        PinSpoConfig config = PinSpoConfig.get();
        config.floorGuide = config.floorGuide.nextFloor();
        config.save();
        hidden = false;
        if (config.floorGuide == PinGuide.Guide.OFF) {
            plot = null;
        } else if (plot == null) {
            scan();
        }
        return config.floorGuide;
    }

    /** Turns the wall lattice on or off, switching the floor guide on too if it was off. */
    public static boolean toggleVertical() {
        PinSpoConfig config = PinSpoConfig.get();
        config.plotVertical = !config.plotVertical;
        if (config.plotVertical && config.floorGuide == PinGuide.Guide.OFF) {
            // Vertical lines are divisions of the same guide, so there has to be a guide to divide.
            config.floorGuide = PinGuide.Guide.THIRDS;
        }
        config.save();
        hidden = false;
        if (config.plotVertical && plot == null) {
            scan();
        }
        return config.plotVertical;
    }

    /** Steps the wall lattice to the next height, returning the height in blocks it now uses. */
    public static int cycleVerticalHeight() {
        PinSpoConfig config = PinSpoConfig.get();
        int index = 0;
        for (int i = 0; i < HEIGHTS.length; i++) {
            if (HEIGHTS[i] == config.plotVerticalHeight) {
                index = i;
                break;
            }
        }
        config.plotVerticalHeight = HEIGHTS[(index + 1) % HEIGHTS.length];
        config.save();
        return verticalHeight(plot);
    }

    /** The wall lattice's height in blocks: the configured one, or the plot's shorter side when automatic. */
    private static int verticalHeight(@Nullable Plot current) {
        int configured = PinSpoConfig.get().plotVerticalHeight;
        if (configured > 0) {
            return configured;
        }
        return current == null ? 16 : Math.min(current.width(), current.depth());
    }

    /** Measures the floor again from where the player is standing now. */
    public static void rescan() {
        hidden = false;
        scan();
    }

    /**
     * Measures the plot as soon as the player is standing on one and then leaves it alone, so the lines stay
     * where the round started instead of following the player around their build.
     */
    public static void tick(Minecraft client) {
        if (plot != null || PinSpoConfig.get().floorGuide == PinGuide.Guide.OFF) {
            return;
        }
        if (client.level == null || client.player == null) {
            ticksUntilScan = 0;
            return;
        }
        if (ticksUntilScan > 0) {
            ticksUntilScan--;
            return;
        }
        ticksUntilScan = RETRY_TICKS;
        scan();
    }

    public static boolean hasPlot() {
        return plot != null;
    }

    /** Side lengths of the measured plot in blocks, or an empty array when nothing is measured. */
    public static int[] size() {
        Plot current = plot;
        return current == null ? new int[0] : new int[]{current.width(), current.depth()};
    }

    public static void setHidden(boolean newHidden) {
        hidden = newHidden;
    }

    /** Forgets the plot, e.g. when leaving a server or when a new round starts. */
    public static void reset() {
        plot = null;
        ticksUntilScan = 0;
    }

    /**
     * Finds the platform under the player: the block the floor is made of (white terracotta, on Hypixel)
     * flood-filled outwards, which gives the plot's bounds without asking the server anything.
     */
    public static void scan() {
        plot = null;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            return;
        }
        BlockPos start = floorUnder(level, player.blockPosition());
        if (start == null) {
            return;
        }
        Block floor = level.getBlockState(start).getBlock();

        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start);
        int minX = start.getX();
        int maxX = start.getX();
        int minZ = start.getZ();
        int maxZ = start.getZ();
        while (!queue.isEmpty() && seen.size() <= MAX_BLOCKS) {
            BlockPos current = queue.poll();
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            for (BlockPos next : new BlockPos[]{
                    current.east(), current.west(), current.north(), current.south()}) {
                if (Math.abs(next.getX() - start.getX()) > SCAN_RADIUS
                        || Math.abs(next.getZ() - start.getZ()) > SCAN_RADIUS
                        || !seen.add(next.asLong())) {
                    continue;
                }
                if (level.getBlockState(next).is(floor)) {
                    queue.add(next);
                }
            }
        }

        if (maxX - minX + 1 < MIN_SIDE || maxZ - minZ + 1 < MIN_SIDE) {
            return;
        }
        plot = new Plot(minX, minZ, maxX, maxZ, start.getY() + 1.0F);
    }

    /** The first block at or below the player's feet that is not air, within {@link #MAX_DROP}. */
    @Nullable
    private static BlockPos floorUnder(ClientLevel level, BlockPos feet) {
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos candidate = feet.below(drop);
            BlockState state = level.getBlockState(candidate);
            if (!state.isAir() && state.isSolidRender()) {
                return candidate;
            }
        }
        return null;
    }

    /** Draws the floor guide; called every frame while a world is being rendered. */
    public static void render(LevelRenderContext context) {
        PinGuide.Guide guide = PinSpoConfig.get().floorGuide;
        Plot current = plot;
        if (hidden || guide == PinGuide.Guide.OFF || current == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        context.submitNodeCollector().submitCustomGeometry(
                matrices, RenderTypes.lines(), (pose, buffer) -> draw(buffer, pose, guide, current));
        matrices.popPose();
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose pose, PinGuide.Guide guide, Plot current) {
        float west = current.minX();
        float north = current.minZ();
        float east = current.maxX() + 1.0F;
        float south = current.maxZ() + 1.0F;
        float y = current.surfaceY() + LIFT;
        float[] fractions = fractions(guide);
        lattice(buffer, pose, west, north, east, south, y, fractions);
        if (guide == PinGuide.Guide.DIAGONALS) {
            line(buffer, pose, west, y, north, east, y, south, LINE_COLOUR);
            line(buffer, pose, east, y, north, west, y, south, LINE_COLOUR);
        }
        // Outline, so the plot's own edges are as readable as the divisions.
        outline(buffer, pose, west, north, east, south, y, LINE_COLOUR);
        if (PinSpoConfig.get().plotVertical) {
            walls(buffer, pose, west, north, east, south, y, verticalHeight(current), fractions);
        }
    }

    /** Where a guide puts its division lines along each side, as fractions of the side's length. */
    private static float[] fractions(PinGuide.Guide guide) {
        return switch (guide) {
            case THIRDS -> new float[]{1.0F / 3.0F, 2.0F / 3.0F};
            case GOLDEN -> new float[]{0.382F, 0.618F};
            case QUARTERS -> new float[]{0.25F, 0.5F, 0.75F};
            case CENTRE -> new float[]{0.5F};
            case GRID -> new float[]{0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F};
            case OFF, SPIRAL, DIAGONALS -> new float[0];
        };
    }

    private static void lattice(VertexConsumer buffer, PoseStack.Pose pose, float west, float north,
                               float east, float south, float y, float... fractions) {
        for (float fraction : fractions) {
            float x = west + (east - west) * fraction;
            float z = north + (south - north) * fraction;
            line(buffer, pose, x, y, north, x, y, south, LINE_COLOUR);
            line(buffer, pose, west, y, z, east, y, z, LINE_COLOUR);
        }
    }

    /**
     * The same divisions carried upwards: a column at every division point of the plot's edges, plus a ring
     * at every division of the height, so proportions can be judged off the ground as well as on it.
     */
    private static void walls(VertexConsumer buffer, PoseStack.Pose pose, float west, float north,
                              float east, float south, float y, int height, float[] fractions) {
        float top = y + height;
        for (float corner : new float[]{0.0F, 1.0F}) {
            float x = west + (east - west) * corner;
            float z = north + (south - north) * corner;
            line(buffer, pose, x, y, north, x, top, north, VERTICAL_COLOUR);
            line(buffer, pose, x, y, south, x, top, south, VERTICAL_COLOUR);
            line(buffer, pose, west, y, z, west, top, z, VERTICAL_COLOUR);
            line(buffer, pose, east, y, z, east, top, z, VERTICAL_COLOUR);
        }
        for (float fraction : fractions) {
            float x = west + (east - west) * fraction;
            float z = north + (south - north) * fraction;
            line(buffer, pose, x, y, north, x, top, north, VERTICAL_COLOUR);
            line(buffer, pose, x, y, south, x, top, south, VERTICAL_COLOUR);
            line(buffer, pose, west, y, z, west, top, z, VERTICAL_COLOUR);
            line(buffer, pose, east, y, z, east, top, z, VERTICAL_COLOUR);
            outline(buffer, pose, west, north, east, south, y + height * fraction, VERTICAL_COLOUR);
        }
        outline(buffer, pose, west, north, east, south, top, VERTICAL_COLOUR);
    }

    private static void outline(VertexConsumer buffer, PoseStack.Pose pose, float west, float north,
                                float east, float south, float y, int colour) {
        line(buffer, pose, west, y, north, east, y, north, colour);
        line(buffer, pose, west, y, south, east, y, south, colour);
        line(buffer, pose, west, y, north, west, y, south, colour);
        line(buffer, pose, east, y, north, east, y, south, colour);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float fromX, float fromY, float fromZ,
                             float toX, float toY, float toZ, int colour) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        float deltaZ = toZ - fromZ;
        float length = Math.max(0.0001F, (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
        float normalX = deltaX / length;
        float normalY = deltaY / length;
        float normalZ = deltaZ / length;
        buffer.addVertex(pose, fromX, fromY, fromZ)
                .setColor(colour)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, toX, toY, toZ)
                .setColor(colour)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(LINE_WIDTH);
    }
}
