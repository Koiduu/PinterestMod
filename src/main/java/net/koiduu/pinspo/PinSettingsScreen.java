package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;

/** Settings tab: overlay appearance on the left, Build Battle options on the right. */
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

        // Both panels have to fit the viewport, so the column width follows the window rather than a cap.
        columnWidth = Math.max(90, Math.min(210, (width - MARGIN * 2 - 48) / 2));
        leftX = MARGIN + 8;
        rightX = width - MARGIN - 8 - columnWidth;
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
        PinButton removePin = PinButton.of(leftX, y, columnWidth, WIDGET_HEIGHT,
                Component.translatable("option.pinspo.remove_pin"), () -> {
                    PinnedImage.unpin();
                    rebuild();
                });
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
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.showCredit)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.show_credit"),
                        (button, value) -> {
                            config.showCredit = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .builder(PinGuide.Guide::label, config.guide)
                .withValues(PinGuide.Guide.values())
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.guide"),
                        (button, guide) -> {
                            config.guide = guide;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .builder(PinGuide.Guide::label, config.floorGuide)
                .withValues(PinGuide.Guide.OFF, PinGuide.Guide.THIRDS, PinGuide.Guide.GOLDEN,
                        PinGuide.Guide.DIAGONALS, PinGuide.Guide.CENTRE, PinGuide.Guide.QUARTERS,
                        PinGuide.Guide.GRID)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.plot_guide"),
                        (button, guide) -> {
                            config.floorGuide = guide;
                            config.save();
                            if (guide == PinGuide.Guide.OFF) {
                                PlotGrid.reset();
                            } else {
                                PlotGrid.setHidden(false);
                                PlotGrid.scan();
                            }
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.plotVertical)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.plot_vertical"),
                        (button, value) -> {
                            config.plotVertical = value;
                            config.save();
                            if (value) {
                                PlotGrid.setHidden(false);
                                if (!PlotGrid.hasPlot()) {
                                    PlotGrid.scan();
                                }
                            }
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(PinButton.of(rightX, y, columnWidth, WIDGET_HEIGHT,
                heightLabel(), () -> {
                    PlotGrid.cycleVerticalHeight();
                    rebuild();
                }));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(CycleButton
                .onOffBuilder(config.blurBackdrop)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.blur_backdrop"),
                        (button, value) -> {
                            config.blurBackdrop = value;
                            config.save();
                        }));
    }

    /** "Auto" for a height that follows the plot's own size, otherwise the block count. */
    private Component heightLabel() {
        Component value = config.plotVerticalHeight == 0
                ? Component.translatable("option.pinspo.plot_height.auto")
                : Component.literal(config.plotVerticalHeight + " "
                        + Component.translatable("option.pinspo.blocks").getString());
        return Component.translatable("option.pinspo.plot_height", value);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bottom = height - FOOTER_HEIGHT - 6;
        renderPanel(guiGraphics, MARGIN, CONTENT_TOP, leftX + columnWidth + 8, bottom);
        renderPanel(guiGraphics, rightX - 8, CONTENT_TOP, width - MARGIN, bottom);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.overlay"), leftX, columnTop);
        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.build_battle"), rightX, columnTop);
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
