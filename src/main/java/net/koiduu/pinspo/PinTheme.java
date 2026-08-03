package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;

/** The mod's own flat, Pinterest-flavoured look: colours plus the few primitives every screen draws. */
public final class PinTheme {

    public static final int ACCENT = 0xFFE60023;
    public static final int ACCENT_DIM = 0xFF8C1020;
    public static final int TEXT = 0xFFF2F2F5;
    public static final int TEXT_MUTED = 0xFF9BA0A8;
    public static final int TEXT_DISABLED = 0xFF6A6E75;

    public static final int BACKDROP = 0xE6121216;
    public static final int BAR = 0xF01A1A20;
    public static final int PANEL = 0xC01F1F26;
    public static final int CARD = 0xFF23232B;
    public static final int CARD_HOVER = 0xFF2E2E38;
    public static final int BORDER = 0xFF35353F;
    public static final int BORDER_BRIGHT = 0xFF52525E;

    private PinTheme() {
    }

    /** A filled rectangle with its four corner pixels cut, which reads as a soft rounded card. */
    public static void roundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x + 1, y, x + width - 1, y + height, color);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, color);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** Draws a one-pixel rounded outline. */
    public static void roundedOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x + 1, y, x + width - 1, y + 1, color);
        guiGraphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, color);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A card: rounded fill plus outline, brighter while hovered. */
    public static void card(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean hovered) {
        roundedRect(guiGraphics, x, y, width, height, hovered ? CARD_HOVER : CARD);
        roundedOutline(guiGraphics, x, y, width, height, hovered ? ACCENT : BORDER);
    }

    /** A translucent content panel with a subtle border. */
    public static void panel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        roundedRect(guiGraphics, x, y, width, height, PANEL);
        roundedOutline(guiGraphics, x, y, width, height, BORDER);
    }

    /** A section label with a short accent rule under it. */
    public static void sectionHeader(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
                                     net.minecraft.network.chat.Component label, int x, int y) {
        guiGraphics.drawString(font, label, x, y, TEXT, false);
        guiGraphics.fill(x, y + 10, x + font.width(label), y + 11, ACCENT);
    }
}
