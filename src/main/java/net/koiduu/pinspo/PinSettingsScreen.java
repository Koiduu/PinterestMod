package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;

/** Settings tab: overlay appearance on the left, Build Battle and browser options on the right. */
public class PinSettingsScreen extends PinTabScreen {

    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;
    private static final int HEADER_HEIGHT = 14;

    private final PinSpoConfig config = PinSpoConfig.get();

    private int columnWidth;
    private int leftX;
    private int rightX;
    private int columnTop;

    public PinSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.settings"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.SETTINGS;
    }

    @Override
    protected void init() {
        addTabs();

        columnWidth = Math.min(210, (width - MARGIN * 3) / 2);
        leftX = MARGIN + 8;
        rightX = leftX + columnWidth + MARGIN + 8;
        columnTop = CONTENT_TOP + 6;

        int y = columnTop + HEADER_HEIGHT;
        addRenderableWidget(percentSlider(leftX, y, "option.pinspo.opacity", config.opacity,
                value -> config.opacity = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(leftX, y, "option.pinspo.scale", config.scale,
                value -> config.scale = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(leftX, y, "option.pinspo.offset_x", config.offsetX,
                value -> config.offsetX = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(leftX, y, "option.pinspo.offset_y", config.offsetY,
                value -> config.offsetY = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .builder((PinSpoConfig.Corner corner) ->
                                Component.translatable("option.pinspo.corner." + corner.name().toLowerCase()),
                        config.corner)
                .withValues(PinSpoConfig.Corner.values())
                .create(leftX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.corner"),
                        (button, corner) -> {
                            config.corner = corner;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.preferOriginalResolution)
                .create(leftX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.prefer_original"),
                        (button, value) -> {
                            config.preferOriginalResolution = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        Button removePin = Button
                .builder(Component.translatable("option.pinspo.remove_pin"), button -> {
                    PinnedImage.unpin();
                    rebuild();
                })
                .bounds(leftX, y, columnWidth, WIDGET_HEIGHT)
                .build();
        removePin.active = PinnedImage.isPinned();
        addRenderableWidget(removePin);

        y = columnTop + HEADER_HEIGHT;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.buildBattleMode)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.build_battle"),
                        (button, value) -> {
                            config.buildBattleMode = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.buildBattleRandomPin)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.build_battle_random"),
                        (button, value) -> {
                            config.buildBattleRandomPin = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING + HEADER_HEIGHT;
        addRenderableWidget(CycleButton
                .builder((Integer value) -> Component.literal(value + "p"),
                        nearestQuality(config.maxBrowserWidth))
                .withValues(640, 800, 960, 1280, 1600)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.browser_quality"),
                        (button, value) -> {
                            config.maxBrowserWidth = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(rightX, y, "option.pinspo.browser_window", config.browserWindowScale,
                value -> config.browserWindowScale = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(Button
                .builder(Component.translatable("option.pinspo.browse"),
                        button -> minecraft.setScreen(new PinterestBrowserScreen(this)))
                .bounds(rightX, y, columnWidth, WIDGET_HEIGHT)
                .build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bottom = height - FOOTER_HEIGHT - 6;
        renderPanel(guiGraphics, MARGIN, CONTENT_TOP, MARGIN + columnWidth + 16, bottom);
        renderPanel(guiGraphics, rightX - 8, CONTENT_TOP, rightX + columnWidth + 8, bottom);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, Component.translatable("screen.pinspo.section.overlay"),
                leftX, columnTop, COLOR_MUTED, false);
        guiGraphics.drawString(font, Component.translatable("screen.pinspo.section.build_battle"),
                rightX, columnTop, COLOR_MUTED, false);
        guiGraphics.drawString(font, Component.translatable("screen.pinspo.section.browser"),
                rightX, columnTop + HEADER_HEIGHT + (WIDGET_HEIGHT + SPACING) * 2, COLOR_MUTED, false);
    }

    private static Integer nearestQuality(int width) {
        int best = 960;
        for (int candidate : new int[] {640, 800, 960, 1280, 1600}) {
            if (Math.abs(candidate - width) < Math.abs(best - width)) {
                best = candidate;
            }
        }
        return best;
    }

    private AbstractSliderButton percentSlider(int x, int y, String translationKey, float initialValue, DoubleConsumer setter) {
        return new AbstractSliderButton(x, y, columnWidth, WIDGET_HEIGHT, Component.empty(), initialValue) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(translationKey, Math.round(value * 100.0D) + "%"));
            }

            @Override
            protected void applyValue() {
                setter.accept(value);
                config.save();
            }
        };
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }
}
