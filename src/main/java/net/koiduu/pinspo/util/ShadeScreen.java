package net.koiduu.pinspo.util;

import net.koiduu.pinspo.BlockPalette;
import net.koiduu.pinspo.PinButton;
import net.koiduu.pinspo.PinSpoClient;
import net.koiduu.pinspo.PinTheme;
import net.koiduu.pinspo.build.BuildTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shade shifter: pick a block, slide from darkest to brightest, and every matching block nearby becomes that
 * shade. The run of shades comes from the block palette, so it follows the resource pack's real colours.
 */
public class ShadeScreen extends Screen {

    private static final int MAX_RADIUS = 32;
    private static final int MAX_CONNECTED = 60_000;
    private static boolean connectedOnly = true;
    private static int radius = 12;

    @Nullable
    private final Screen parent;
    private final Block source;
    private final BlockPos origin;
    private final List<BlockPalette.Entry> ramp;
    private final int sourceIndex;
    /** Where the source block was when the screen opened; kept so the slider can be moved again and again. */
    private final List<BlockPos> connected = new ArrayList<>();
    private final List<BlockPos> nearby = new ArrayList<>();
    @Nullable
    private List<BlockPos> targets;
    private int index;
    @Nullable
    private Component feedback;

    private int panelX;
    private int panelWidth;
    private int stripX;
    private int stripY;
    private int iconStep;

    private ShadeScreen(@Nullable Screen parent, Block source, BlockPos origin, List<BlockPalette.Entry> ramp) {
        super(Component.translatable("screen.pinspo.shade"));
        this.parent = parent;
        this.source = source;
        this.origin = origin;
        this.ramp = ramp;
        int found = 0;
        for (int i = 0; i < ramp.size(); i++) {
            if (ramp.get(i).block() == source) {
                found = i;
            }
        }
        this.sourceIndex = found;
        this.index = found;
        capture();
    }

    /** Opens the shifter on the block under the crosshair, or says why it can't. */
    @Nullable
    public static Component openAtCrosshair(Minecraft client, @Nullable Screen parent) {
        if (client.level == null || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return Component.translatable("message.pinspo.shade_look");
        }
        Block block = client.level.getBlockState(hit.getBlockPos()).getBlock();
        if (!BlockPalette.isReady()) {
            BlockPalette.prepare();
            return Component.translatable("message.pinspo.shade_loading");
        }
        List<BlockPalette.Entry> ramp = BlockPalette.shadeRamp(block, 64);
        if (ramp.isEmpty()) {
            return Component.translatable("message.pinspo.shade_not_full", block.getName());
        }
        PinSpoClient.LOGGER.info("Shade ramp for {}: {}", BuiltInRegistries.BLOCK.getKey(block),
                ramp.stream().map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block()).getPath()).toList());
        client.setScreenAndShow(new ShadeScreen(parent, block, hit.getBlockPos().immutable(), ramp));
        return null;
    }

    private void capture() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
            for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
                for (int z = -MAX_RADIUS; z <= MAX_RADIUS; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.getBlockState(cursor).is(source)) {
                        nearby.add(cursor.immutable());
                    }
                }
            }
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && connected.size() < MAX_CONNECTED) {
            BlockPos pos = queue.poll();
            connected.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (reach(next) <= MAX_RADIUS && seen.add(next) && level.getBlockState(next).is(source)) {
                    queue.add(next);
                }
            }
        }
        PinSpoClient.LOGGER.info("Shade shifter on {} at {}: {} touching, {} within {} blocks",
                BuiltInRegistries.BLOCK.getKey(source), origin.toShortString(), connected.size(), nearby.size(), MAX_RADIUS);
    }

    private int reach(BlockPos pos) {
        return Math.max(Math.abs(pos.getX() - origin.getX()),
                Math.max(Math.abs(pos.getY() - origin.getY()), Math.abs(pos.getZ() - origin.getZ())));
    }

    private List<BlockPos> targets() {
        if (targets == null) {
            targets = (connectedOnly ? connected : nearby).stream().filter(pos -> reach(pos) <= radius).toList();
        }
        return targets;
    }

    private static boolean live() {
        return BuildTools.mode() == BuildTools.Mode.DIRECT;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 24, 460);
        panelX = (width - panelWidth) / 2;
        int inner = panelWidth - 16;
        int x = panelX + 8;
        int half = (inner - 4) / 2;
        stripY = 44;
        iconStep = Math.max(8, Math.min(18, (inner - 16) / Math.max(1, ramp.size() - 1)));
        stripX = panelX + (panelWidth - (iconStep * (ramp.size() - 1) + 16)) / 2;

        int y = stripY + 40;
        int steps = ramp.size() - 1;
        addRenderableWidget(new AbstractSliderButton(x, y, inner, 20, Component.empty(),
                steps == 0 ? 0.0 : index / (double) steps) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("screen.pinspo.shade_slider",
                        ramp.get(index).block().getName(), index + 1, ramp.size()));
            }

            @Override
            protected void applyValue() {
                index = (int) Math.round(value * steps);
            }

            @Override
            public void onRelease(MouseButtonEvent event) {
                super.onRelease(event);
                if (live()) {
                    apply();
                }
            }
        });
        y += 26;
        addRenderableWidget(CycleButton.onOffBuilder(connectedOnly).create(x, y, half, 20,
                Component.translatable("screen.pinspo.shade_connected"), (button, value) -> {
                    connectedOnly = value;
                    targets = null;
                }));
        addRenderableWidget(new AbstractSliderButton(x + half + 4, y, half, 20, Component.empty(),
                (radius - 1) / (double) (MAX_RADIUS - 1)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("screen.pinspo.shade_radius", radius));
            }

            @Override
            protected void applyValue() {
                radius = 1 + (int) Math.round(value * (MAX_RADIUS - 1));
                targets = null;
            }
        });

        int buttonY = Math.min(height - 30, y + 64);
        int third = (inner - 8) / 3;
        addRenderableWidget(PinButton.primary(x, buttonY, third, 20,
                Component.translatable("screen.pinspo.shade_apply"), this::apply));
        addRenderableWidget(PinButton.of(x + third + 4, buttonY, third, 20,
                Component.translatable("screen.pinspo.shade_undo"), () -> {
                    BuildTools.undo();
                    feedback = Component.literal(BuildTools.lastMessage());
                }));
        addRenderableWidget(PinButton.of(x + (third + 4) * 2, buttonY, third, 20,
                Component.translatable("gui.done"), this::onClose));
    }

    private void apply() {
        List<BlockPos> positions = targets();
        if (positions.isEmpty()) {
            feedback = Component.translatable("screen.pinspo.shade_none");
            return;
        }
        BlockState state = ramp.get(index).block().defaultBlockState();
        Map<BlockPos, BlockState> changes = new HashMap<>();
        for (BlockPos pos : positions) {
            changes.put(pos, state);
        }
        feedback = Component.literal(BuildTools.applyEdit(
                Component.translatable("screen.pinspo.shade").getString(), changes));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.y() >= stripY - 2 && event.y() < stripY + 26 && event.x() >= stripX - 4
                && event.x() < stripX + iconStep * (ramp.size() - 1) + 20) {
            index = Math.clamp(Math.round((float) (event.x() - stripX - 8) / iconStep), 0, ramp.size() - 1);
            rebuildWidgets();
            if (live()) {
                apply();
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        PinTheme.panel(guiGraphics, panelX, 12, panelWidth, height - 24);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        int x = panelX + 8;
        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.shade_title", source.getName()), x, 22);

        for (int i = 0; i < ramp.size(); i++) {
            int ix = stripX + i * iconStep;
            guiGraphics.fill(ix, stripY + 19, ix + Math.max(iconStep, 2), stripY + 23, 0xFF000000 | ramp.get(i).rgb());
            if (i != index) {
                guiGraphics.item(new ItemStack(ramp.get(i).block()), ix, stripY);
            }
            if (i == sourceIndex) {
                guiGraphics.fill(ix + 6, stripY + 25, ix + 10, stripY + 27, PinTheme.TEXT);
            }
        }
        int sx = stripX + index * iconStep;
        PinTheme.roundedRect(guiGraphics, sx - 3, stripY - 3, 22, 22, PinTheme.CARD_HOVER);
        PinTheme.roundedOutline(guiGraphics, sx - 3, stripY - 3, 22, 22, PinTheme.ACCENT);
        guiGraphics.item(new ItemStack(ramp.get(index).block()), sx, stripY);

        Component darkest = Component.translatable("screen.pinspo.shade_darkest");
        Component brightest = Component.translatable("screen.pinspo.shade_brightest");
        guiGraphics.text(font, darkest, x, stripY + 29, PinTheme.TEXT_MUTED, false);
        guiGraphics.text(font, brightest, panelX + panelWidth - 8 - font.width(brightest), stripY + 29, PinTheme.TEXT_MUTED, false);

        int infoY = stripY + 40 + 26 + 28;
        Component mode = Component.translatable("screen.pinspo.shade_mode." + BuildTools.mode().name().toLowerCase(java.util.Locale.ROOT));
        guiGraphics.text(font, Component.translatable("screen.pinspo.shade_info", targets().size(), source.getName(), mode),
                x, infoY, PinTheme.TEXT_SOFT, false);
        Component hint = feedback != null ? feedback
                : Component.translatable(live() ? "screen.pinspo.shade_live" : "screen.pinspo.shade_press_apply");
        guiGraphics.text(font, font.plainSubstrByWidth(hint.getString(), panelWidth - 16), x, infoY + 12,
                feedback != null ? PinTheme.TEXT : PinTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
