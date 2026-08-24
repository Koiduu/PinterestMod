package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Settings tab: overlay appearance on the left, Build Battle options on the right. Both columns scroll
 * together, so no option can end up below the window however small it is.
 */
public class PinSettingsScreen extends PinTabScreen {

    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;
    private static final int HEADER_HEIGHT = 14;

    private final PinSpoConfig config = PinSpoConfig.get();

    private int columnWidth;
    private int leftX;
    private int rightX;
    private int columnTop;
    /** Forgetting the taste profile cannot be undone, so the button asks once before it does it. */
    private boolean confirmForget;

    /** Every option widget, with the y it would sit at unscrolled, so scrolling can just move them. */
    private final List<AbstractWidget> options = new ArrayList<>();
    private final List<Integer> unscrolledY = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int contentTop;
    private int contentBottom;
    private boolean draggingBar;

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
        options.clear();
        unscrolledY.clear();

        // Both panels have to fit the viewport, so the column width follows the window rather than a cap.
        columnWidth = Math.max(90, Math.min(210, (width - MARGIN * 2 - 48) / 2));
        leftX = MARGIN + 8;
        rightX = width - MARGIN - 8 - columnWidth;
        columnTop = CONTENT_TOP + 6;

        int y = columnTop + HEADER_HEIGHT;
        addOption(percentSlider(leftX, y, "option.pinspo.opacity", config.opacity,
                value -> config.opacity = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addOption(percentSlider(leftX, y, "option.pinspo.scale", config.scale,
                value -> config.scale = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addOption(percentSlider(leftX, y, "option.pinspo.offset_x", config.offsetX,
                value -> config.offsetX = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addOption(percentSlider(leftX, y, "option.pinspo.offset_y", config.offsetY,
                value -> config.offsetY = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
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
        addOption(CycleButton
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
        addOption(removePin);
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
                .onOffBuilder(config.personalise)
                .create(leftX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.personalise"),
                        (button, value) -> {
                            config.personalise = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        PinButton forget = PinButton.of(leftX, y, columnWidth, WIDGET_HEIGHT,
                Component.translatable(confirmForget
                        ? "option.pinspo.forget_taste.confirm"
                        : "option.pinspo.forget_taste"), () -> {
                    if (confirmForget) {
                        PinTaste.forget();
                    }
                    confirmForget = !confirmForget;
                    rebuild();
                });
        forget.active = confirmForget || PinTaste.hasProfile();
        addOption(forget);

        y = columnTop + HEADER_HEIGHT;
        addOption(CycleButton
                .onOffBuilder(config.buildBattleMode)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.build_battle"),
                        (button, value) -> {
                            config.buildBattleMode = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
                .onOffBuilder(config.buildBattleRandomPin)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.build_battle_random"),
                        (button, value) -> {
                            config.buildBattleRandomPin = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
                .onOffBuilder(config.showCredit)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.show_credit"),
                        (button, value) -> {
                            config.showCredit = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
                .builder(PinGuide.Guide::label, config.guide)
                .withValues(PinGuide.Guide.values())
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.guide"),
                        (button, guide) -> {
                            config.guide = guide;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
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
        addOption(CycleButton
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
        addOption(PinButton.of(rightX, y, columnWidth, WIDGET_HEIGHT,
                heightLabel(), () -> {
                    PlotGrid.cycleVerticalHeight();
                    rebuild();
                }));
        y += WIDGET_HEIGHT + SPACING;
        addOption(CycleButton
                .onOffBuilder(config.blurBackdrop)
                .create(rightX, y, columnWidth, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.blur_backdrop"),
                        (button, value) -> {
                            config.blurBackdrop = value;
                            config.save();
                        }));

        contentTop = columnTop + HEADER_HEIGHT;
        contentBottom = height - FOOTER_HEIGHT - 12;
        int tallest = 0;
        for (int optionY : unscrolledY) {
            tallest = Math.max(tallest, optionY + WIDGET_HEIGHT);
        }
        maxScroll = Math.max(0, tallest - contentBottom);
        applyScroll(scroll);
    }

    /** Adds an option to the scrolling columns, remembering where it sits when nothing is scrolled. */
    private <T extends AbstractWidget> T addOption(T option) {
        options.add(option);
        unscrolledY.add(option.getY());
        return addRenderableWidget(option);
    }

    /**
     * Moves the options by {@code target}, hiding any that would fall outside the panels — hidden widgets
     * cannot be clicked either, so a half-scrolled option can never be hit through the footer.
     */
    private void applyScroll(int target) {
        scroll = Math.clamp(target, 0, maxScroll);
        for (int i = 0; i < options.size(); i++) {
            AbstractWidget option = options.get(i);
            int y = unscrolledY.get(i) - scroll;
            option.setY(y);
            option.visible = y >= contentTop && y + option.getHeight() <= contentBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            applyScroll(scroll - (int) Math.round(verticalAmount * (WIDGET_HEIGHT + SPACING)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (maxScroll > 0 && event.x() >= width - MARGIN + 2 && event.x() <= width - 2
                && event.y() >= contentTop && event.y() <= contentBottom) {
            draggingBar = true;
            scrollToBar(event.y());
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar) {
            scrollToBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    /** Puts the grabbed point of the bar under the cursor. */
    private void scrollToBar(double mouseY) {
        int track = contentBottom - contentTop;
        double fraction = (mouseY - contentTop) / Math.max(1, track);
        applyScroll((int) Math.round(fraction * maxScroll));
    }

    /** The bar beside the panels: how far down the options the view is, and a handle to drag. */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }
        int track = contentBottom - contentTop;
        int handle = Math.max(16, track * track / (track + maxScroll));
        int top = contentTop + (track - handle) * scroll / maxScroll;
        int x = width - MARGIN + 2;
        PinTheme.roundedRect(guiGraphics, x, contentTop, 4, track, PinTheme.CARD);
        PinTheme.roundedRect(guiGraphics, x, top, 4, handle, PinTheme.ACCENT);
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
        renderScrollbar(guiGraphics);
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
