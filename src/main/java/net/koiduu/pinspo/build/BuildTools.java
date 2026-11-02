package net.koiduu.pinspo.build;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.koiduu.pinspo.CameraRenderer;
import net.koiduu.pinspo.PinSpoClient;
import net.koiduu.pinspo.PinTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PinSpo's own building kit. Build mode turns the mouse into a long-reach selection and brush tool; every edit
 * goes through {@link #apply}, which writes blocks directly in singleplayer, sends {@code /setblock}/{@code /fill}
 * when the server lets you use them, and otherwise (Hypixel, no op) turns the edit into see-through ghost blocks
 * that you build over by hand. Nothing is ever sent to a server that hasn't given you those commands.
 */
public final class BuildTools {

    public enum Tool {
        SELECT, PAINT, ERASE, RECOLOR, SMOOTH;

        public Tool next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Mode {
        DIRECT, COMMANDS, GHOST
    }

    public static final int MAX_BLOCKS = 300_000;
    private static final int MAX_REACH = 160;
    private static final int COMMANDS_PER_TICK = 40;
    private static final int MAX_GHOSTS_DRAWN = 6000;
    private static final int HISTORY = 30;

    private static boolean active;
    private static Tool tool = Tool.SELECT;
    private static int radius = 3;
    private static boolean forceGhost;
    @Nullable
    private static BlockPos first;
    @Nullable
    private static BlockPos second;
    private static Map<BlockPos, BlockState> clipboard = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockState> GHOSTS = new HashMap<>();
    private static final Deque<String> COMMAND_QUEUE = new ArrayDeque<>();
    private static final Deque<Edit> UNDO = new ArrayDeque<>();
    private static final Deque<Edit> REDO = new ArrayDeque<>();
    private static int brushCooldown;
    private static int ghostPrune;

    private record Edit(String label, Map<BlockPos, BlockState> before, Map<BlockPos, BlockState> after) {
    }

    private BuildTools() {
    }

    // ---- state ----

    public static boolean isActive() {
        return active;
    }

    public static void toggle() {
        active = !active;
        tell(active ? "Build mode on: left-click = corner 1, right-click = corner 2. Shift+N for tools"
                : "Build mode off");
    }

    public static Tool tool() {
        return tool;
    }

    public static void setTool(Tool value) {
        tool = value;
    }

    public static int radius() {
        return radius;
    }

    public static void setRadius(int value) {
        radius = Math.clamp(value, 1, 32);
    }

    public static boolean forceGhost() {
        return forceGhost;
    }

    public static void setForceGhost(boolean value) {
        forceGhost = value;
    }

    @Nullable
    public static BlockPos first() {
        return first;
    }

    @Nullable
    public static BlockPos second() {
        return second;
    }

    public static boolean hasSelection() {
        return first != null && second != null;
    }

    public static void clearSelection() {
        first = null;
        second = null;
    }

    public static int clipboardSize() {
        return clipboard.size();
    }

    public static int ghostCount() {
        return GHOSTS.size();
    }

    public static void clearGhosts() {
        GHOSTS.clear();
    }

    public static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    public static boolean canRedo() {
        return !REDO.isEmpty();
    }

    public static Mode mode() {
        Minecraft client = Minecraft.getInstance();
        if (forceGhost || client.player == null || client.getConnection() == null) {
            return Mode.GHOST;
        }
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            return Mode.DIRECT;
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip.toLowerCase(java.util.Locale.ROOT).contains("hypixel")) {
            return Mode.GHOST;
        }
        var root = client.getConnection().getCommands().getRoot();
        return root.getChild("setblock") != null && root.getChild("fill") != null ? Mode.COMMANDS : Mode.GHOST;
    }

    // ---- input ----

    /** Runs before vanilla handles keys, so clicks used by build mode never reach the world. */
    public static void onStartTick(Minecraft client) {
        if (!active || client.player == null || client.level == null || client.gui.screen() != null) {
            return;
        }
        boolean attack = false;
        while (client.options.keyAttack.consumeClick()) {
            attack = true;
        }
        boolean use = false;
        while (client.options.keyUse.consumeClick()) {
            use = true;
        }
        boolean holdingUse = client.options.keyUse.isDown();
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);

        if (brushCooldown > 0) {
            brushCooldown--;
        }
        if (tool == Tool.SELECT) {
            if (attack || use) {
                BlockPos target = target(client, false);
                if (target == null) {
                    tell("Nothing in reach (" + MAX_REACH + " blocks)");
                } else if (attack) {
                    first = target;
                    tell("Corner 1: " + target.toShortString() + size());
                } else {
                    second = target;
                    tell("Corner 2: " + target.toShortString() + size());
                }
            }
        } else if ((use || holdingUse) && brushCooldown == 0) {
            brushCooldown = 3;
            brush(client);
        } else if (attack) {
            tool = Tool.SELECT;
            tell("Tool: Select");
        }
    }

    public static void onEndTick(Minecraft client) {
        if (client.getConnection() != null) {
            for (int i = 0; i < COMMANDS_PER_TICK && !COMMAND_QUEUE.isEmpty(); i++) {
                client.getConnection().sendCommand(COMMAND_QUEUE.poll());
            }
        } else {
            COMMAND_QUEUE.clear();
        }
        if (client.level != null && !GHOSTS.isEmpty() && ++ghostPrune >= 10) {
            ghostPrune = 0;
            GHOSTS.entrySet().removeIf(entry -> client.level.getBlockState(entry.getKey()).equals(entry.getValue()));
        }
        if (!active || client.gui.screen() != null || client.player == null) {
            return;
        }
        boolean control = client.hasControlDown();
        if (control && BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_Z)) {
            undo();
        } else if (control && BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_Y)) {
            redo();
        } else if (control && BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_C)) {
            copy(false);
        } else if (control && BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_V)) {
            paste();
        } else if (BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_LBRACKET)) {
            setRadius(radius - 1);
            tell("Brush radius " + radius);
        } else if (BuildKeys.pressed(com.mojang.blaze3d.platform.InputConstants.KEY_RBRACKET)) {
            setRadius(radius + 1);
            tell("Brush radius " + radius);
        }
        BuildKeys.endTick();
    }

    @Nullable
    private static BlockPos target(Minecraft client, boolean offset) {
        HitResult hit = client.player.pick(MAX_REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return offset ? block.getBlockPos().relative(block.getDirection()) : block.getBlockPos();
    }

    @Nullable
    private static BlockHitResult hit(Minecraft client) {
        HitResult hit = client.player.pick(MAX_REACH, 1.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
    }

    private static String size() {
        if (!hasSelection()) {
            return "";
        }
        BlockPos min = min();
        BlockPos max = max();
        return "  (" + (max.getX() - min.getX() + 1) + "×" + (max.getY() - min.getY() + 1) + "×"
                + (max.getZ() - min.getZ() + 1) + ")";
    }

    private static BlockPos min() {
        return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos max() {
        return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public static long volume() {
        if (!hasSelection()) {
            return 0;
        }
        BlockPos min = min();
        BlockPos max = max();
        return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    /** The block in the main hand, or air when {@code allowAir} and the hand is empty. */
    @Nullable
    private static BlockState material(boolean offhand) {
        Minecraft client = Minecraft.getInstance();
        ItemStack stack = offhand ? client.player.getOffhandItem() : client.player.getMainHandItem();
        if (stack.getItem() instanceof BlockItem item) {
            return item.getBlock().defaultBlockState();
        }
        return null;
    }

    @Nullable
    private static BlockState requireMaterial() {
        BlockState state = material(false);
        if (state == null) {
            tell("Hold the block you want to build with");
        }
        return state;
    }

    private static boolean requireSelection() {
        if (!hasSelection()) {
            tell("Select two corners first (build mode: left- and right-click)");
            return false;
        }
        if (volume() > MAX_BLOCKS) {
            tell("Selection is too big (" + volume() + " blocks, max " + MAX_BLOCKS + ")");
            return false;
        }
        return true;
    }

    // ---- selection operations ----

    public static void fill() {
        BlockState state = requireMaterial();
        if (state != null && requireSelection()) {
            Map<BlockPos, BlockState> changes = new HashMap<>();
            forEach((pos, edge) -> changes.put(pos, state));
            apply("Fill", changes);
        }
    }

    public static void clear() {
        if (requireSelection()) {
            Map<BlockPos, BlockState> changes = new HashMap<>();
            forEach((pos, edge) -> changes.put(pos, Blocks.AIR.defaultBlockState()));
            apply("Clear", changes);
        }
    }

    /** Swaps every block matching the off-hand block (or every solid block if the off-hand is empty). */
    public static void replace() {
        BlockState state = requireMaterial();
        if (state == null || !requireSelection()) {
            return;
        }
        BlockState from = material(true);
        Minecraft client = Minecraft.getInstance();
        Map<BlockPos, BlockState> changes = new HashMap<>();
        forEach((pos, edge) -> {
            BlockState current = client.level.getBlockState(pos);
            if (from == null ? !current.isAir() : current.getBlock() == from.getBlock()) {
                changes.put(pos, state);
            }
        });
        apply("Replace", changes);
    }

    public static void walls() {
        BlockState state = requireMaterial();
        if (state == null || !requireSelection()) {
            return;
        }
        BlockPos min = min();
        BlockPos max = max();
        Map<BlockPos, BlockState> changes = new HashMap<>();
        forEach((pos, edge) -> {
            if (pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getZ() == min.getZ()
                    || pos.getZ() == max.getZ()) {
                changes.put(pos, state);
            }
        });
        apply("Walls", changes);
    }

    /** A shell: outside faces become the held block, the inside becomes air. */
    public static void hollow() {
        BlockState state = requireMaterial();
        if (state == null || !requireSelection()) {
            return;
        }
        Map<BlockPos, BlockState> changes = new HashMap<>();
        forEach((pos, edge) -> changes.put(pos, edge ? state : Blocks.AIR.defaultBlockState()));
        apply("Hollow box", changes);
    }

    /** Repeats the selection once more in the direction you're facing and moves the selection onto the copy. */
    public static void stack() {
        if (!requireSelection()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Direction facing = client.player.getXRot() < -50 ? Direction.UP
                : client.player.getXRot() > 50 ? Direction.DOWN : client.player.getDirection();
        BlockPos min = min();
        BlockPos max = max();
        int length = switch (facing.getAxis()) {
            case X -> max.getX() - min.getX() + 1;
            case Y -> max.getY() - min.getY() + 1;
            case Z -> max.getZ() - min.getZ() + 1;
        };
        BlockPos shift = BlockPos.ZERO.relative(facing, length);
        Map<BlockPos, BlockState> changes = new HashMap<>();
        forEach((pos, edge) -> changes.put(pos.offset(shift), client.level.getBlockState(pos)));
        apply("Stack " + facing.getName(), changes);
        first = first.offset(shift);
        second = second.offset(shift);
    }

    private interface Visitor {
        void visit(BlockPos pos, boolean edge);
    }

    private static void forEach(Visitor visitor) {
        BlockPos min = min();
        BlockPos max = max();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    boolean edge = x == min.getX() || x == max.getX() || y == min.getY() || y == max.getY()
                            || z == min.getZ() || z == max.getZ();
                    visitor.visit(new BlockPos(x, y, z), edge);
                }
            }
        }
    }

    // ---- clipboard ----

    public static void copy(boolean cut) {
        if (!requireSelection()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        BlockPos min = min();
        Map<BlockPos, BlockState> copied = new LinkedHashMap<>();
        Map<BlockPos, BlockState> removed = new HashMap<>();
        forEach((pos, edge) -> {
            BlockState state = client.level.getBlockState(pos);
            if (!state.isAir()) {
                copied.put(pos.subtract(min), state);
                removed.put(pos, Blocks.AIR.defaultBlockState());
            }
        });
        clipboard = copied;
        tell((cut ? "Cut " : "Copied ") + copied.size() + " blocks. Ctrl+V pastes where you look");
        if (cut) {
            apply("Cut", removed);
        }
    }

    /** Pastes with the clipboard's lowest corner on the face you're looking at. */
    public static void paste() {
        if (clipboard.isEmpty()) {
            tell("Clipboard is empty: select something and press Ctrl+C");
            return;
        }
        Minecraft client = Minecraft.getInstance();
        BlockPos anchor = target(client, true);
        if (anchor == null) {
            tell("Look at a block to paste onto");
            return;
        }
        Map<BlockPos, BlockState> changes = new HashMap<>();
        clipboard.forEach((offset, state) -> changes.put(anchor.offset(offset), state));
        apply("Paste", changes);
    }

    public static void rotateClipboard() {
        transform(Rotation.CLOCKWISE_90, Mirror.NONE);
        tell("Clipboard turned 90°");
    }

    public static void flipClipboard() {
        transform(Rotation.NONE, Mirror.FRONT_BACK);
        tell("Clipboard flipped");
    }

    private static void transform(Rotation rotation, Mirror mirror) {
        if (clipboard.isEmpty()) {
            tell("Clipboard is empty");
            return;
        }
        Map<BlockPos, BlockState> moved = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : clipboard.keySet()) {
            int x = pos.getX();
            int z = pos.getZ();
            if (mirror == Mirror.FRONT_BACK) {
                x = -x;
            }
            if (rotation == Rotation.CLOCKWISE_90) {
                int t = x;
                x = -z;
                z = t;
            }
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
        }
        for (Map.Entry<BlockPos, BlockState> entry : clipboard.entrySet()) {
            int x = entry.getKey().getX();
            int z = entry.getKey().getZ();
            if (mirror == Mirror.FRONT_BACK) {
                x = -x;
            }
            if (rotation == Rotation.CLOCKWISE_90) {
                int t = x;
                x = -z;
                z = t;
            }
            moved.put(new BlockPos(x - minX, entry.getKey().getY(), z - minZ),
                    entry.getValue().mirror(mirror).rotate(rotation));
        }
        clipboard = moved;
    }

    // ---- shapes and brushes ----

    public enum Shape {
        SPHERE, DOME, CYLINDER, CONE
    }

    /** Builds a shape centred on the block face you're looking at. */
    public static void shape(Shape shape, boolean hollow) {
        BlockState state = requireMaterial();
        if (state == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        BlockPos centre = target(client, true);
        if (centre == null) {
            tell("Look at a block to build on");
            return;
        }
        int r = radius;
        Map<BlockPos, BlockState> changes = new HashMap<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r * 2; y++) {
                for (int z = -r; z <= r; z++) {
                    if (inside(shape, x, y, z, r) && !(hollow && inside(shape, x, y, z, r - 1)
                            && neighboursInside(shape, x, y, z, r))) {
                        changes.put(centre.offset(x, y, z), state);
                    }
                }
            }
        }
        apply((hollow ? "Hollow " : "") + shape.name().toLowerCase(java.util.Locale.ROOT), changes);
    }

    private static boolean neighboursInside(Shape shape, int x, int y, int z, int r) {
        for (Direction direction : Direction.values()) {
            if (!inside(shape, x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ(), r)) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(Shape shape, int x, int y, int z, int r) {
        double rr = (r + 0.5) * (r + 0.5);
        return switch (shape) {
            case SPHERE -> x * x + y * y + z * z <= rr;
            case DOME -> y >= 0 && x * x + y * y + z * z <= rr;
            case CYLINDER -> y >= 0 && y < r * 2 && x * x + z * z <= rr;
            case CONE -> {
                int height = r * 2;
                double scaled = (r + 0.5) * (1.0 - (double) y / height);
                yield y >= 0 && y < height && x * x + z * z <= scaled * scaled;
            }
        };
    }

    private static void brush(Minecraft client) {
        BlockHitResult hit = hit(client);
        if (hit == null) {
            return;
        }
        BlockPos centre = tool == Tool.PAINT ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        BlockState state = tool == Tool.PAINT || tool == Tool.RECOLOR ? requireMaterial() : null;
        if ((tool == Tool.PAINT || tool == Tool.RECOLOR) && state == null) {
            return;
        }
        int r = radius;
        double rr = (r + 0.5) * (r + 0.5);
        Map<BlockPos, BlockState> changes = new HashMap<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > rr) {
                        continue;
                    }
                    BlockPos pos = centre.offset(x, y, z);
                    BlockState current = effective(client, pos);
                    switch (tool) {
                        case PAINT -> {
                            if (current.isAir()) {
                                changes.put(pos, state);
                            }
                        }
                        case ERASE -> {
                            if (!current.isAir()) {
                                changes.put(pos, Blocks.AIR.defaultBlockState());
                            }
                        }
                        case RECOLOR -> {
                            if (!current.isAir() && exposed(client, pos)) {
                                changes.put(pos, state);
                            }
                        }
                        case SMOOTH -> smooth(client, pos, current, changes);
                        default -> {
                        }
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            apply(tool.name().charAt(0) + tool.name().substring(1).toLowerCase(java.util.Locale.ROOT) + " brush",
                    changes);
        }
    }

    /** What a position will look like once pending ghosts are built, so brushes keep working in ghost mode. */
    private static BlockState effective(Minecraft client, BlockPos pos) {
        BlockState ghost = mode() == Mode.GHOST ? GHOSTS.get(pos) : null;
        return ghost != null ? ghost : client.level.getBlockState(pos);
    }

    private static boolean exposed(Minecraft client, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (effective(client, pos.relative(direction)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Majority vote of the 26 neighbours: lone bumps get removed and small dents get filled. */
    private static void smooth(Minecraft client, BlockPos pos, BlockState current, Map<BlockPos, BlockState> out) {
        int solid = 0;
        Map<Block, Integer> counts = new HashMap<>();
        BlockState common = null;
        int best = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockState state = effective(client, pos.offset(x, y, z));
                    if (!state.isAir()) {
                        solid++;
                        int count = counts.merge(state.getBlock(), 1, Integer::sum);
                        if (count > best) {
                            best = count;
                            common = state.getBlock().defaultBlockState();
                        }
                    }
                }
            }
        }
        if (current.isAir() && solid >= 15 && common != null) {
            out.put(pos, common);
        } else if (!current.isAir() && solid <= 10) {
            out.put(pos, Blocks.AIR.defaultBlockState());
        }
    }

    // ---- applying edits ----

    private static void apply(String label, Map<BlockPos, BlockState> changes) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || changes.isEmpty()) {
            tell(label + ": nothing to change");
            return;
        }
        if (changes.size() > MAX_BLOCKS) {
            tell(label + " is too big (" + changes.size() + " blocks)");
            return;
        }
        Mode mode = mode();
        Map<BlockPos, BlockState> before = new HashMap<>();
        Map<BlockPos, BlockState> after = new HashMap<>();
        changes.forEach((pos, state) -> {
            BlockState current = effective(client, pos);
            if (!current.equals(state)) {
                before.put(pos.immutable(), current);
                after.put(pos.immutable(), state);
            }
        });
        if (after.isEmpty()) {
            tell(label + ": already done");
            return;
        }
        write(mode, after);
        UNDO.push(new Edit(label, before, after));
        while (UNDO.size() > HISTORY) {
            UNDO.removeLast();
        }
        REDO.clear();
        PinSpoClient.LOGGER.info("Build tools: {} changed {} blocks ({})", label, after.size(), mode);
        tell(label + ": " + after.size() + (mode == Mode.GHOST ? " ghost blocks to build" : " blocks")
                + "  ·  Ctrl+Z undoes");
    }

    public static void undo() {
        Edit edit = UNDO.poll();
        if (edit == null) {
            tell("Nothing to undo");
            return;
        }
        write(mode(), edit.before());
        REDO.push(edit);
        tell("Undid " + edit.label());
    }

    public static void redo() {
        Edit edit = REDO.poll();
        if (edit == null) {
            tell("Nothing to redo");
            return;
        }
        write(mode(), edit.after());
        UNDO.push(edit);
        tell("Redid " + edit.label());
    }

    private static void write(Mode mode, Map<BlockPos, BlockState> blocks) {
        Minecraft client = Minecraft.getInstance();
        switch (mode) {
            case DIRECT -> {
                var server = client.getSingleplayerServer();
                var dimension = client.level.dimension();
                Map<BlockPos, BlockState> copy = new HashMap<>(blocks);
                server.execute(() -> {
                    ServerLevel level = server.getLevel(dimension);
                    if (level != null) {
                        copy.forEach((pos, state) -> level.setBlock(pos, state,
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                    }
                });
            }
            case COMMANDS -> queueCommands(blocks);
            case GHOST -> blocks.forEach((pos, state) -> {
                if (client.level.getBlockState(pos).equals(state)) {
                    GHOSTS.remove(pos);
                } else {
                    GHOSTS.put(pos, state);
                }
            });
        }
    }

    /** Runs of the same block along X become one {@code /fill}, everything else a {@code /setblock}. */
    private static void queueCommands(Map<BlockPos, BlockState> blocks) {
        List<BlockPos> sorted = new ArrayList<>(blocks.keySet());
        sorted.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
                : a.getZ() != b.getZ() ? Integer.compare(a.getZ(), b.getZ()) : Integer.compare(a.getX(), b.getX()));
        int i = 0;
        while (i < sorted.size()) {
            BlockPos start = sorted.get(i);
            BlockState state = blocks.get(start);
            int j = i + 1;
            while (j < sorted.size()) {
                BlockPos next = sorted.get(j);
                if (next.getY() != start.getY() || next.getZ() != start.getZ()
                        || next.getX() != start.getX() + (j - i) || !blocks.get(next).equals(state)) {
                    break;
                }
                j++;
            }
            String block = BlockStateParser.serialize(state);
            BlockPos end = sorted.get(j - 1);
            COMMAND_QUEUE.add(j - i == 1
                    ? "setblock " + start.getX() + " " + start.getY() + " " + start.getZ() + " " + block
                    : "fill " + start.getX() + " " + start.getY() + " " + start.getZ() + " "
                            + end.getX() + " " + end.getY() + " " + end.getZ() + " " + block);
            i = j;
        }
    }

    // ---- drawing ----

    public static void render(LevelRenderContext context) {
        if (CameraRenderer.isRendering() || (!active && GHOSTS.isEmpty())) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        BlockHitResult hover = active && client.gui.screen() == null ? hit(client) : null;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(),
                (pose, buffer) -> {
                    if (active && first != null) {
                        BlockPos a = first;
                        BlockPos b = second != null ? second : first;
                        box(buffer, pose, camera, Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                                Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1,
                                Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1,
                                PinTheme.ACCENT, 3.0F, 0.02);
                        box(buffer, pose, camera, a.getX(), a.getY(), a.getZ(), a.getX() + 1, a.getY() + 1,
                                a.getZ() + 1, 0xFF4FD1FF, 2.0F, 0.04);
                        if (second != null) {
                            box(buffer, pose, camera, b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1,
                                    b.getZ() + 1, 0xFFFFC94F, 2.0F, 0.04);
                        }
                    }
                    if (hover != null) {
                        BlockPos c = tool == Tool.PAINT ? hover.getBlockPos().relative(hover.getDirection())
                                : hover.getBlockPos();
                        int r = tool == Tool.SELECT ? 0 : radius;
                        box(buffer, pose, camera, c.getX() - r, c.getY() - r, c.getZ() - r, c.getX() + r + 1,
                                c.getY() + r + 1, c.getZ() + r + 1, 0xC0FFFFFF, 1.5F, 0.01);
                    }
                    int drawn = 0;
                    BlockPos eye = client.player.blockPosition();
                    for (Map.Entry<BlockPos, BlockState> ghost : GHOSTS.entrySet()) {
                        BlockPos p = ghost.getKey();
                        if (p.distManhattan(eye) > 96 || drawn++ > MAX_GHOSTS_DRAWN) {
                            continue;
                        }
                        int colour = ghost.getValue().isAir() ? 0xFFFF4040
                                : 0xFF000000 | ghost.getValue().getMapColor(client.level, p).col;
                        box(buffer, pose, camera, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1,
                                p.getZ() + 1, colour, 1.5F, -0.08);
                    }
                });
    }

    private static void box(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, double x0, double y0, double z0,
                            double x1, double y1, double z1, int colour, float width, double grow) {
        float ax = (float) (x0 - grow - camera.x);
        float ay = (float) (y0 - grow - camera.y);
        float az = (float) (z0 - grow - camera.z);
        float bx = (float) (x1 + grow - camera.x);
        float by = (float) (y1 + grow - camera.y);
        float bz = (float) (z1 + grow - camera.z);
        line(buffer, pose, ax, ay, az, bx, ay, az, colour, width);
        line(buffer, pose, ax, by, az, bx, by, az, colour, width);
        line(buffer, pose, ax, ay, bz, bx, ay, bz, colour, width);
        line(buffer, pose, ax, by, bz, bx, by, bz, colour, width);
        line(buffer, pose, ax, ay, az, ax, by, az, colour, width);
        line(buffer, pose, bx, ay, az, bx, by, az, colour, width);
        line(buffer, pose, ax, ay, bz, ax, by, bz, colour, width);
        line(buffer, pose, bx, ay, bz, bx, by, bz, colour, width);
        line(buffer, pose, ax, ay, az, ax, ay, bz, colour, width);
        line(buffer, pose, bx, ay, az, bx, ay, bz, colour, width);
        line(buffer, pose, ax, by, az, ax, by, bz, colour, width);
        line(buffer, pose, bx, by, az, bx, by, bz, colour, width);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0,
                             float x1, float y1, float z1, int colour, float width) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x0, y0, z0).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, x1, y1, z1).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    /** A small status strip at the top of the screen while build mode is on. */
    public static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || (!active && GHOSTS.isEmpty())) {
            return;
        }
        String modeText = switch (mode()) {
            case DIRECT -> "edits world";
            case COMMANDS -> "edits via commands";
            case GHOST -> "ghost guides";
        };
        String text = active
                ? "BUILD  ·  " + toolName(tool) + (tool == Tool.SELECT ? "" : " r" + radius)
                        + (hasSelection() ? "  ·  " + size().trim() : "") + "  ·  " + modeText
                        + (GHOSTS.isEmpty() ? "" : "  ·  " + GHOSTS.size() + " left")
                : GHOSTS.size() + " ghost blocks left to build";
        int width = client.font.width(text) + 12;
        int x = (graphics.guiWidth() - width) / 2;
        PinTheme.roundedRect(graphics, x, 4, width, 14, 0xC0121216);
        graphics.fill(x + 2, 5, x + 4, 17, PinTheme.ACCENT);
        graphics.text(client.font, Component.literal(text), x + 8, 7, PinTheme.TEXT, false);
    }

    public static String toolName(Tool value) {
        return switch (value) {
            case SELECT -> "Select";
            case PAINT -> "Build brush";
            case ERASE -> "Erase brush";
            case RECOLOR -> "Recolour brush";
            case SMOOTH -> "Smooth brush";
        };
    }

    public static void onDisconnect() {
        active = false;
        clearSelection();
        GHOSTS.clear();
        COMMAND_QUEUE.clear();
        UNDO.clear();
        REDO.clear();
    }

    static void tell(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.literal(message));
        }
    }
}
