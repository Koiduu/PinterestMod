package net.koiduu.pinspo.util;

import net.koiduu.pinspo.BlockPalette;
import net.koiduu.pinspo.CameraPoints;
import net.koiduu.pinspo.CameraRenderer;
import net.koiduu.pinspo.PinButton;
import net.koiduu.pinspo.PinSpoClient;
import net.koiduu.pinspo.PinTabScreen;
import net.koiduu.pinspo.PinTheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The Utilities tab. Five small sections instead of one crowded page: view (fog and far rendering), cameras,
 * hologram lines and the shade shifter, macros, and a list of every shortcut.
 */
public class UtilitiesScreen extends PinTabScreen {

    public enum Section {
        VIEW, CAMERAS, BUILD, MACROS, KEYS
    }

    private static final int H = 20;
    private static final int ROW = 23;
    private static final int HEADER = 15;
    private static Section section = Section.VIEW;

    private final Utilities settings = Utilities.get();
    private int leftX;
    private int rightX;
    private int columnWidth;
    private int top;
    private boolean confirmClear;
    @Nullable
    private Component feedback;
    /** Where each section's notes go, worked out in init so text and widgets never overlap. */
    private int leftNoteY;
    private int rightNoteY;

    public UtilitiesScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.utilities"), parent);
    }

    public static UtilitiesScreen open(Section start) {
        section = start;
        return new UtilitiesScreen(null);
    }

    @Override
    protected Tab tab() {
        return Tab.UTILS;
    }

    @Override
    protected void init() {
        addTabs();
        Section[] sections = Section.values();
        int sectionWidth = Math.max(56, Math.min(92, (width - MARGIN * 2) / sections.length - 3));
        int sx = MARGIN;
        for (Section value : sections) {
            PinButton button = new PinButton(sx, CONTENT_TOP - 2, sectionWidth, 16,
                    Component.translatable("screen.pinspo.util_section." + value.name().toLowerCase(Locale.ROOT)),
                    PinButton.Style.TAB, () -> {
                        section = value;
                        confirmClear = false;
                        feedback = null;
                        rebuildWidgets();
                    });
            button.selected(value == section);
            addRenderableWidget(button);
            sx += sectionWidth + 3;
        }

        columnWidth = Math.max(120, Math.min(230, (width - MARGIN * 2 - 24) / 2));
        leftX = MARGIN + 4;
        rightX = width - MARGIN - 4 - columnWidth;
        top = CONTENT_TOP + 22;
        switch (section) {
            case VIEW -> initView();
            case CAMERAS -> initCameras();
            case BUILD -> initBuild();
            case MACROS -> initMacros();
            case KEYS -> addRenderableWidget(PinButton.of(MARGIN, height - FOOTER_HEIGHT + 6, 110, 20,
                    Component.translatable("screen.pinspo.keys_change"),
                    () -> minecraft.setScreenAndShow(new KeyBindsScreen(this, minecraft.options))));
        }
    }

    // ---- sections ----

    private void initView() {
        int y = top + HEADER;
        int half = (columnWidth - 4) / 2;
        addRenderableWidget(toggle(leftX, y, columnWidth, "option.pinspo.fog_all", settings.fog, value -> settings.fog = value));
        y += ROW;
        addRenderableWidget(toggle(leftX, y, half, "option.pinspo.fog_distance", settings.distanceFog, value -> settings.distanceFog = value));
        addRenderableWidget(toggle(leftX + half + 4, y, half, "option.pinspo.fog_sky", settings.skyFog, value -> settings.skyFog = value));
        y += ROW;
        addRenderableWidget(toggle(leftX, y, half, "option.pinspo.fog_water", settings.waterFog, value -> settings.waterFog = value));
        addRenderableWidget(toggle(leftX + half + 4, y, half, "option.pinspo.fog_lava", settings.lavaFog, value -> settings.lavaFog = value));
        y += ROW;
        addRenderableWidget(toggle(leftX, y, half, "option.pinspo.fog_snow", settings.snowFog, value -> settings.snowFog = value));
        addRenderableWidget(toggle(leftX + half + 4, y, half, "option.pinspo.fog_blind", settings.blindFog, value -> settings.blindFog = value));
        leftNoteY = y + ROW + 2;

        y = top + HEADER;
        addRenderableWidget(scale(rightX, y, columnWidth, "option.pinspo.entity_scale", settings.entityScale,
                Utilities.MAX_ENTITY_SCALE, value -> settings.entityScale = (float) value));
        y += ROW;
        addRenderableWidget(scale(rightX, y, columnWidth, "option.pinspo.block_entity_scale", settings.blockEntityScale,
                Utilities.MAX_ENTITY_SCALE, value -> settings.blockEntityScale = (float) value));
        rightNoteY = y + ROW + 2;
    }

    private void initCameras() {
        int y = top + HEADER;
        addRenderableWidget(scale(leftX, y, columnWidth, "option.pinspo.camera_range", settings.cameraRange,
                Utilities.MAX_CAMERA_RANGE, value -> settings.cameraRange = (float) value));
        y += ROW;
        addRenderableWidget(toggle(leftX, y, columnWidth, "option.pinspo.camera_fog", settings.cameraFog, value -> settings.cameraFog = value));
        y += ROW;
        addRenderableWidget(PinButton.of(leftX, y, columnWidth, H, withKey("screen.pinspo.cameras_toggle", PinSpoClient.CAMERAS_TOGGLE_KEY), () -> {
            CameraPoints.toggleAll();
            rebuildWidgets();
        }));
        y += ROW;
        addRenderableWidget(PinButton.of(leftX, y, columnWidth, H, withKey("screen.pinspo.cameras_shot", PinSpoClient.CAMERA_SHOT_KEY), () -> {
            int taken = 0;
            for (CameraPoints.Point point : CameraPoints.visibleHere()) {
                taken += CameraRenderer.screenshot(point) ? 1 : 0;
            }
            feedback = Component.translatable(taken > 0 ? "screen.pinspo.camera_shot_saved" : "message.pinspo.camera_shot_wait", taken);
        }));
        leftNoteY = y + ROW + 2;

        y = top + HEADER;
        boolean canGo = CameraPoints.canTeleport(minecraft);
        for (CameraPoints.Point point : CameraPoints.here()) {
            int right = rightX + columnWidth;
            addRenderableWidget(PinButton.of(right - 44, y, 44, H, Component.translatable(
                    point.visible ? "screen.pinspo.camera_hide" : "screen.pinspo.camera_show"), () -> {
                CameraPoints.toggle(point);
                rebuildWidgets();
            }));
            PinButton shot = PinButton.of(right - 44 - 4 - 40, y, 40, H, Component.translatable("screen.pinspo.camera_shot"), () ->
                    feedback = Component.translatable(CameraRenderer.screenshot(point)
                            ? "screen.pinspo.camera_shot_saved" : "message.pinspo.camera_shot_wait", 1));
            shot.active = point.visible;
            addRenderableWidget(shot);
            PinButton go = PinButton.primary(right - 44 - 4 - 40 - 4 - 36, y, 36, H, Component.translatable("screen.pinspo.camera_go"), () -> {
                CameraPoints.teleport(minecraft, point);
                onClose();
            });
            go.active = canGo;
            addRenderableWidget(go);
            y += ROW;
        }
        rightNoteY = y + 4;
    }

    private void initBuild() {
        int y = top + HEADER + noteHeight(holoHow(), columnWidth) + 4;
        addRenderableWidget(toggle(leftX, y, columnWidth, "option.pinspo.holo_show", settings.holograms, value -> settings.holograms = value));
        y += ROW;
        int half = (columnWidth - 4) / 2;
        addRenderableWidget(PinButton.of(leftX, y, half, H, Component.translatable("screen.pinspo.holo_undo"), Holograms::undo));
        addRenderableWidget(PinButton.of(leftX + half + 4, y, half, H, Component.translatable(
                confirmClear ? "screen.pinspo.holo_clear_confirm" : "screen.pinspo.holo_clear"), () -> {
            if (confirmClear) {
                Holograms.clearHere();
                confirmClear = false;
            } else {
                confirmClear = true;
            }
            rebuildWidgets();
        }));
        leftNoteY = y + ROW + 2;

        BlockPalette.prepare();
        y = top + HEADER + noteHeight(shadeHow(), columnWidth) + 4;
        addRenderableWidget(PinButton.primary(rightX, y, columnWidth, H, Component.translatable("screen.pinspo.shade_open"), () -> {
            Component problem = ShadeScreen.openAtCrosshair(minecraft, this);
            if (problem != null) {
                feedback = problem;
            }
        }));
        rightNoteY = y + ROW + 2;
    }

    private void initMacros() {
        List<Macros.Macro> macros = Macros.all();
        int perColumn = Macros.MAX_MACROS / 2;
        for (int i = 0; i < macros.size(); i++) {
            Macros.Macro macro = macros.get(i);
            int x = i < perColumn ? leftX : rightX;
            int y = top + HEADER + (i % perColumn) * ROW;
            String label = (macro.isRunning() ? "● " : "") + macro.name + "  ·  " + macro.keyName().getString();
            addRenderableWidget(PinButton.of(x, y, columnWidth - 24, H, Component.literal(label),
                    () -> minecraft.setScreenAndShow(new MacroEditScreen(this, macro))));
            addRenderableWidget(PinButton.of(x + columnWidth - 20, y, 20, H, Component.literal("×"), () -> {
                macro.running = false;
                Macros.all().remove(macro);
                Macros.save();
                rebuildWidgets();
            }));
        }
        PinButton add = PinButton.primary(MARGIN, height - FOOTER_HEIGHT + 6, 110, 20,
                Component.translatable("screen.pinspo.macro_new"), () -> minecraft.setScreenAndShow(new MacroEditScreen(this, null)));
        add.active = macros.size() < Macros.MAX_MACROS;
        addRenderableWidget(add);
        leftNoteY = top + HEADER + Math.min(perColumn, Math.max(1, macros.size())) * ROW + 2;
    }

    // ---- widgets ----

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(value).create(x, y, w, H, Component.translatable(key), (button, now) -> {
            setter.accept(now);
            settings.save();
        });
    }

    /** A 1x to {@code max}x multiplier slider, in quarter steps. */
    private AbstractSliderButton scale(int x, int y, int w, String key, float initial, float max, DoubleConsumer setter) {
        return new AbstractSliderButton(x, y, w, H, Component.empty(), (initial - 1.0) / (max - 1.0)) {
            {
                updateMessage();
            }

            private double current() {
                return 1.0 + Math.round(value * (max - 1.0) * 4.0) / 4.0;
            }

            @Override
            protected void updateMessage() {
                double now = current();
                setMessage(Component.translatable(key, now == 1.0
                        ? Component.translatable("option.pinspo.scale_normal").getString()
                        : String.format(Locale.ROOT, "%.2fx", now).replace(".00x", "x").replace("0x", "x")));
            }

            @Override
            protected void applyValue() {
                setter.accept(current());
                settings.save();
            }
        };
    }

    private static Component withKey(String key, KeyMapping mapping) {
        return Component.translatable(key).append(Component.literal("  (")).append(mapping.getTranslatedKeyMessage())
                .append(Component.literal(")"));
    }

    private static Component holoHow() {
        return Component.translatable("screen.pinspo.holo_how", PinSpoClient.HOLOGRAM_KEY.getTranslatedKeyMessage(),
                PinSpoClient.HOLOGRAM_KEY.getTranslatedKeyMessage());
    }

    private static Component shadeHow() {
        return Component.translatable("screen.pinspo.shade_how", PinSpoClient.SHADE_KEY.getTranslatedKeyMessage());
    }

    private int noteHeight(Component text, int w) {
        return font.split(text, w).size() * 10;
    }

    private int note(GuiGraphicsExtractor guiGraphics, Component text, int x, int y, int w, int colour) {
        for (FormattedCharSequence line : font.split(text, w)) {
            if (y > height - FOOTER_HEIGHT - 10) {
                break;
            }
            guiGraphics.text(font, line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        switch (section) {
            case VIEW -> {
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.fog"), leftX, top);
                note(guiGraphics, Component.translatable("screen.pinspo.fog_note", PinSpoClient.FOG_KEY.getTranslatedKeyMessage()),
                        leftX, leftNoteY, columnWidth, COLOR_MUTED);
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.far"), rightX, top);
                note(guiGraphics, Component.translatable("screen.pinspo.far_note"), rightX, rightNoteY, columnWidth, COLOR_MUTED);
            }
            case CAMERAS -> {
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.camera_view"), leftX, top);
                note(guiGraphics, Component.translatable("screen.pinspo.camera_range_note"), leftX, leftNoteY, columnWidth, COLOR_MUTED);
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.camera_list"), rightX, top);
                List<CameraPoints.Point> cameras = CameraPoints.here();
                int y = top + HEADER;
                for (CameraPoints.Point point : cameras) {
                    guiGraphics.fill(rightX, y + 8, rightX + 4, y + 12, point.visible ? PinTheme.ACCENT : PinTheme.BORDER);
                    guiGraphics.text(font, font.plainSubstrByWidth(point.name, columnWidth - 8 - 128 - 4), rightX + 8, y + 6,
                            point.visible ? PinTheme.TEXT_SOFT : COLOR_MUTED, false);
                    y += ROW;
                }
                Component note = cameras.isEmpty()
                        ? Component.translatable("screen.pinspo.camera_none_here", PinSpoClient.CAMERA_KEY.getTranslatedKeyMessage())
                        : CameraPoints.canTeleport(minecraft) ? Component.translatable("screen.pinspo.camera_keys",
                                PinSpoClient.CAMERA_TP_KEY.getTranslatedKeyMessage(), PinSpoClient.CAMERA_SHOT_KEY.getTranslatedKeyMessage())
                        : Component.translatable("screen.pinspo.camera_go_denied");
                note(guiGraphics, note, rightX, rightNoteY, columnWidth, COLOR_MUTED);
            }
            case BUILD -> {
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.holograms"), leftX, top);
                note(guiGraphics, holoHow(), leftX, top + HEADER, columnWidth, COLOR_MUTED);
                note(guiGraphics, Component.translatable("screen.pinspo.holo_count", Holograms.countHere()), leftX, leftNoteY, columnWidth, PinTheme.TEXT_SOFT);
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.shade"), rightX, top);
                note(guiGraphics, shadeHow(), rightX, top + HEADER, columnWidth, COLOR_MUTED);
                note(guiGraphics, Component.translatable(BlockPalette.isReady() ? "screen.pinspo.shade_ready" : "screen.pinspo.shade_palette_loading"),
                        rightX, rightNoteY, columnWidth, COLOR_MUTED);
            }
            case MACROS -> {
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.macros"), leftX, top);
                if (Macros.all().isEmpty()) {
                    note(guiGraphics, Component.translatable("screen.pinspo.macros_how"), leftX, top + HEADER, width - MARGIN * 2 - 8, COLOR_MUTED);
                } else {
                    note(guiGraphics, Component.translatable("screen.pinspo.macros_how"), leftX, leftNoteY, width - MARGIN * 2 - 8, COLOR_MUTED);
                }
            }
            case KEYS -> {
                PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.keys"), leftX, top);
                List<KeyMapping> keys = PinSpoClient.keys();
                int perColumn = (keys.size() + 1) / 2;
                for (int i = 0; i < keys.size(); i++) {
                    KeyMapping mapping = keys.get(i);
                    int x = i < perColumn ? leftX : rightX;
                    int y = top + HEADER + (i % perColumn) * 13;
                    Component keyName = mapping.getTranslatedKeyMessage();
                    int keyWidth = font.width(keyName);
                    // The Controls labels carry a "(sneak to …)" note that doesn't fit here; the notes below cover it.
                    String label = Component.translatable(mapping.getName()).getString().replaceAll("\\s*\\(.*\\)$", "");
                    guiGraphics.text(font, font.plainSubstrByWidth(label, columnWidth - keyWidth - 10), x, y, COLOR_MUTED, false);
                    guiGraphics.text(font, keyName, x + columnWidth - keyWidth, y, mapping.isUnbound() ? PinTheme.TEXT_DISABLED : PinTheme.TEXT, false);
                }
                note(guiGraphics, Component.translatable("screen.pinspo.keys_how"), leftX,
                        top + HEADER + perColumn * 13 + 8, width - MARGIN * 2 - 8, COLOR_MUTED);
            }
        }
        if (feedback != null) {
            guiGraphics.text(font, font.plainSubstrByWidth(feedback.getString(), width - MARGIN * 2 - 220),
                    MARGIN + (section == Section.MACROS || section == Section.KEYS ? 118 : 0),
                    height - FOOTER_HEIGHT + 12, PinTheme.TEXT_SOFT, false);
        }
    }
}
