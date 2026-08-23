package net.koiduu.pinspo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
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
    private static final float LINE_WIDTH = 4.0F;
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

    @Nullable
    private static Plot plot;
    /** Hidden without being forgotten, e.g. during Build Battle voting. */
    private static boolean hidden;

    private PlotGrid() {
    }

    /**
     * Steps to the next floor guide, scanning for the plot when one is switched on. Returns the guide now
     * in use; a null plot afterwards means no floor could be found.
     */
    public static PinGuide.Guide cycle() {
        PinSpoConfig config = PinSpoConfig.get();
        config.floorGuide = config.floorGuide.nextFloor();
        config.save();
        hidden = false;
        if (config.floorGuide == PinGuide.Guide.OFF) {
            plot = null;
        } else {
            scan();
        }
        return config.floorGuide;
    }

    public static boolean hasPlot() {
        return plot != null;
    }

    public static void setHidden(boolean newHidden) {
        hidden = newHidden;
    }

    /** Forgets the plot, e.g. when leaving a server or when a new round starts. */
    public static void reset() {
        plot = null;
    }

    /**
     * Finds the platform under the player: the block the floor is made of (white concrete, on Hypixel)
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
    public static void render(WorldRenderContext context) {
        PinGuide.Guide guide = PinSpoConfig.get().floorGuide;
        Plot current = plot;
        if (hidden || guide == PinGuide.Guide.OFF || current == null) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        MultiBufferSource consumers = context.consumers();
        VertexConsumer buffer = consumers.getBuffer(RenderTypes.lines());
        PoseStack matrices = context.matrices();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = matrices.last();

        float west = current.minX();
        float north = current.minZ();
        float east = current.maxX() + 1.0F;
        float south = current.maxZ() + 1.0F;
        float y = current.surfaceY() + LIFT;
        switch (guide) {
            case THIRDS -> lattice(buffer, pose, west, north, east, south, y, 1.0F / 3.0F, 2.0F / 3.0F);
            case GOLDEN -> lattice(buffer, pose, west, north, east, south, y, 0.382F, 0.618F);
            case QUARTERS -> lattice(buffer, pose, west, north, east, south, y, 0.25F, 0.5F, 0.75F);
            case CENTRE -> lattice(buffer, pose, west, north, east, south, y, 0.5F);
            case GRID -> lattice(buffer, pose, west, north, east, south, y,
                    0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F);
            case DIAGONALS -> {
                line(buffer, pose, west, y, north, east, y, south);
                line(buffer, pose, east, y, north, west, y, south);
            }
            case OFF, SPIRAL -> {
            }
        }
        // Outline, so the plot's own edges are as readable as the divisions.
        line(buffer, pose, west, y, north, east, y, north);
        line(buffer, pose, west, y, south, east, y, south);
        line(buffer, pose, west, y, north, west, y, south);
        line(buffer, pose, east, y, north, east, y, south);

        matrices.popPose();
        if (consumers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(RenderTypes.lines());
        }
    }

    private static void lattice(VertexConsumer buffer, PoseStack.Pose pose, float west, float north,
                               float east, float south, float y, float... fractions) {
        for (float fraction : fractions) {
            float x = west + (east - west) * fraction;
            float z = north + (south - north) * fraction;
            line(buffer, pose, x, y, north, x, y, south);
            line(buffer, pose, west, y, z, east, y, z);
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
                             float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        float deltaZ = toZ - fromZ;
        float length = Math.max(0.0001F, (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
        float normalX = deltaX / length;
        float normalY = deltaY / length;
        float normalZ = deltaZ / length;
        buffer.addVertex(pose, fromX, fromY, fromZ)
                .setColor(LINE_COLOUR)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, toX, toY, toZ)
                .setColor(LINE_COLOUR)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(LINE_WIDTH);
    }
}
