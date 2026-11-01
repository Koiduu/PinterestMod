package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * A slim side panel for adjusting the wall photo while looking at it: the world stays fully visible, so
 * every change can be judged against the build as it happens.
 */
public class WallEditScreen extends Screen {

    private static final int PANEL_WIDTH = 160;
    private static final int ROW = 22;

    @Nullable
    private final Screen parent;
    @Nullable
    private Component feedback;
    /** Where the hint text starts: just under the last button, so it can never sit on top of one. */
    private int hintTop;

    public WallEditScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.wall_edit"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WallPhotos.Photo photo = WallPhotos.mine();
        if (photo == null) {
            onClose();
            return;
        }
        int x = 14;
        int inner = PANEL_WIDTH - 12;
        int y = 34;
        addRenderableWidget(slider(x, y, inner, "option.pinspo.wall_opacity", photo.opacity,
                value -> WallPhotos.setOpacity((float) value), value -> Math.round(value * 100.0D) + "%"));
        y += ROW;
        addRenderableWidget(slider(x, y, inner, "option.pinspo.wall_size", sizeToSlider(photo.size),
                value -> WallPhotos.setSize(sliderToSize(value)),
                value -> blocks(sliderToSize(value))));
        y += ROW + 6;

        int third = (inner - 4) / 3;
        addRenderableWidget(PinButton.of(x + third + 2, y, third, 20, Component.literal("\u2191"),
                () -> WallPhotos.nudge(0, step(), 0)));
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, third, 20, Component.literal("\u2190"),
                () -> WallPhotos.nudge(-step(), 0, 0)));
        addRenderableWidget(PinButton.of(x + third + 2, y, third, 20, Component.literal("\u2193"),
                () -> WallPhotos.nudge(0, -step(), 0)));
        addRenderableWidget(PinButton.of(x + (third + 2) * 2, y, third, 20, Component.literal("\u2192"),
                () -> WallPhotos.nudge(step(), 0, 0)));
        y += ROW;
        int half = (inner - 2) / 2;
        addRenderableWidget(PinButton.of(x, y, half, 20, Component.translatable("option.pinspo.wall_out"),
                () -> WallPhotos.nudge(0, 0, step() / 4.0D)));
        addRenderableWidget(PinButton.of(x + half + 2, y, half, 20, Component.translatable("option.pinspo.wall_in"),
                () -> WallPhotos.nudge(0, 0, -step() / 4.0D)));
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, inner, 20, Component.translatable("option.pinspo.wall_rotate"),
                WallPhotos::rotate));
        y += ROW + 6;

        addRenderableWidget(PinButton.of(x, y, inner, 20, Component.translatable(photo.shared
                ? "option.pinspo.wall_shared.on" : "option.pinspo.wall_shared.off"), () -> {
            String problem = WallPhotos.setShared(!photo.shared);
            feedback = problem == null ? null : Component.translatable(problem);
            rebuild();
        }));
        y += ROW;
        PinButton swap = PinButton.of(x, y, inner, 20, Component.translatable("option.pinspo.wall_use_pin"), () -> {
            WallPhotos.usePinnedImage();
            rebuild();
        });
        swap.active = !PinnedImage.currentUrl().isEmpty() && !PinnedImage.currentUrl().equals(photo.url);
        addRenderableWidget(swap);
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, inner, 20, Component.translatable("option.pinspo.wall_remove"), () -> {
            WallPhotos.remove();
            onClose();
        }));
        hintTop = y + ROW + 6;
        addRenderableWidget(PinButton.primary(x, height - 30, inner, 20, Component.translatable("gui.done"),
                this::onClose));
    }

    /** Shift moves a whole block; otherwise a quarter block, fine enough to line up with pixel art. */
    private static double step() {
        return net.minecraft.client.Minecraft.getInstance().hasShiftDown() ? 1.0D : 0.25D;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        double step = event.hasShiftDown() ? 1.0D : 0.25D;
        switch (event.key()) {
            case InputConstants.KEY_UP -> WallPhotos.nudge(0, step, 0);
            case InputConstants.KEY_DOWN -> WallPhotos.nudge(0, -step, 0);
            case InputConstants.KEY_LEFT -> WallPhotos.nudge(-step, 0, 0);
            case InputConstants.KEY_RIGHT -> WallPhotos.nudge(step, 0, 0);
            case InputConstants.KEY_R -> WallPhotos.rotate();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX > PANEL_WIDTH + 8 && WallPhotos.mine() != null) {
            float size = WallPhotos.mine().size;
            WallPhotos.setSize(size * (verticalAmount > 0 ? 1.1F : 1.0F / 1.1F));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** No blur or dimming: the point of this screen is seeing the photo on the build. */
    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        PinTheme.panel(guiGraphics, 8, 8, PANEL_WIDTH, height - 16);
        PinTheme.sectionHeader(guiGraphics, font, title, 14, 16);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        int textWidth = PANEL_WIDTH - 12;
        int doneTop = height - 30;
        int y = hintTop;
        if (feedback != null) {
            for (var line : font.split(feedback, textWidth)) {
                if (y + 9 > doneTop - 4) {
                    break;
                }
                guiGraphics.text(font, line, 14, y, PinTheme.ACCENT, false);
                y += 10;
            }
            y += 4;
        }
        Component[] hints = {
                Component.translatable("screen.pinspo.wall_hint_keys"),
                Component.translatable("screen.pinspo.wall_hint_scroll"),
                Component.translatable("screen.pinspo.wall_hint_move", PinSpoClient.WALL_KEY.getTranslatedKeyMessage())
        };
        for (Component hint : hints) {
            for (var line : font.split(hint, textWidth)) {
                if (y + 9 > doneTop - 4) {
                    return;
                }
                guiGraphics.text(font, line, 14, y, PinTheme.TEXT_MUTED, false);
                y += 10;
            }
        }
    }

    private static float sliderToSize(double value) {
        return (float) (WallPhotos.MIN_SIZE + (WallPhotos.MAX_SIZE - WallPhotos.MIN_SIZE) * value * value);
    }

    private static double sizeToSlider(float size) {
        return Math.sqrt(Math.clamp((size - WallPhotos.MIN_SIZE) / (WallPhotos.MAX_SIZE - WallPhotos.MIN_SIZE), 0.0F, 1.0F));
    }

    static String blocks(float size) {
        return size < 10.0F ? String.format(java.util.Locale.ROOT, "%.1f", size) : String.valueOf(Math.round(size));
    }

    private AbstractSliderButton slider(int x, int y, int width, String key, double initial,
                                        java.util.function.DoubleConsumer setter,
                                        java.util.function.DoubleFunction<String> label) {
        return new AbstractSliderButton(x, y, width, 20, Component.empty(), initial) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(key, label.apply(value)));
            }

            @Override
            protected void applyValue() {
                setter.accept(value);
            }
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        WallPhotos.flushShare();
        minecraft.setScreenAndShow(parent);
    }
}
