package net.koiduu.pinspo;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;

public class PinSettingsScreen extends Screen {

    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;

    @Nullable
    private final Screen parent;
    private final PinSpoConfig config = PinSpoConfig.get();

    public PinSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - WIDGET_WIDTH) / 2;
        int y = Math.max(24, height / 2 - 115);

        addRenderableWidget(percentSlider(x, y, "option.pinspo.opacity", config.opacity,
                value -> config.opacity = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(x, y, "option.pinspo.scale", config.scale,
                value -> config.scale = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(x, y, "option.pinspo.offset_x", config.offsetX,
                value -> config.offsetX = (float) value));
        y += WIDGET_HEIGHT + SPACING;
        addRenderableWidget(percentSlider(x, y, "option.pinspo.offset_y", config.offsetY,
                value -> config.offsetY = (float) value));
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(CycleButton
                .builder((PinSpoConfig.Corner corner) ->
                                Component.translatable("option.pinspo.corner." + corner.name().toLowerCase()),
                        config.corner)
                .withValues(PinSpoConfig.Corner.values())
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.corner"),
                        (button, corner) -> {
                            config.corner = corner;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(CycleButton
                .onOffBuilder(config.preferOriginalResolution)
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.prefer_original"),
                        (button, value) -> {
                            config.preferOriginalResolution = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(CycleButton
                .builder((Integer value) -> Component.literal(value + "p"),
                        nearestQuality(config.maxBrowserWidth))
                .withValues(640, 800, 960, 1280, 1600)
                .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("option.pinspo.browser_quality"),
                        (button, value) -> {
                            config.maxBrowserWidth = value;
                            config.save();
                        }));
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(percentSlider(x, y, "option.pinspo.browser_window", config.browserWindowScale,
                value -> config.browserWindowScale = (float) value));
        y += WIDGET_HEIGHT + SPACING * 3;

        addRenderableWidget(Button
                .builder(Component.translatable("option.pinspo.browse"),
                        button -> minecraft.setScreen(new PinBrowseScreen(parent)))
                .bounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT)
                .build());
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(Button
                .builder(Component.translatable("option.pinspo.remove_pin"), button -> {
                    PinnedImage.unpin();
                    onClose();
                })
                .bounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT)
                .build());
        y += WIDGET_HEIGHT + SPACING;

        addRenderableWidget(Button
                .builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT)
                .build());
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
        return new AbstractSliderButton(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), initialValue) {
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreen(parent);
    }
}
