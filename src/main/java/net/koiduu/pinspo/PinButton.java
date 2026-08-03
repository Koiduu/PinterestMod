package net.koiduu.pinspo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat themed button, in a neutral and a Pinterest-red primary style. */
public class PinButton extends AbstractWidget {

    /** How the button is filled. */
    public enum Style {
        NEUTRAL, PRIMARY, TAB
    }

    private final Runnable onPress;
    private final Style style;
    /** Tabs use this instead of {@code active} so the current tab still narrates and looks selected. */
    private boolean selected;

    public PinButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    public static PinButton of(int x, int y, int width, int height, Component message, Runnable onPress) {
        return new PinButton(x, y, width, height, message, Style.NEUTRAL, onPress);
    }

    public static PinButton primary(int x, int y, int width, int height, Component message, Runnable onPress) {
        return new PinButton(x, y, width, height, message, Style.PRIMARY, onPress);
    }

    public PinButton selected(boolean newSelected) {
        this.selected = newSelected;
        return this;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        PinButton.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && active;
        int fill;
        int border;
        if (style == Style.PRIMARY) {
            fill = !active ? 0xFF3A2026 : hovered ? PinTheme.ACCENT : PinTheme.ACCENT_DIM;
            border = active ? PinTheme.ACCENT : PinTheme.BORDER;
        } else if (selected) {
            fill = PinTheme.CARD_HOVER;
            border = PinTheme.ACCENT;
        } else {
            fill = hovered ? PinTheme.CARD_HOVER : PinTheme.CARD;
            border = hovered ? PinTheme.BORDER_BRIGHT : PinTheme.BORDER;
        }

        PinTheme.roundedRect(guiGraphics, getX(), getY(), width, height, fill);
        PinTheme.roundedOutline(guiGraphics, getX(), getY(), width, height, border);
        if (style == Style.TAB && selected) {
            guiGraphics.fill(getX() + 2, getY() + height - 2, getX() + width - 2, getY() + height, PinTheme.ACCENT);
        }

        int color = !active ? PinTheme.TEXT_DISABLED : selected || hovered ? PinTheme.TEXT : PinTheme.TEXT_MUTED;
        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), width - 6),
                getX() + (width - Math.min(width - 6, font.width(getMessage()))) / 2,
                getY() + (height - 8) / 2, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
