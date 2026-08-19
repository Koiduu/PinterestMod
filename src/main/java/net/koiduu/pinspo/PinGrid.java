package net.koiduu.pinspo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Scrollable grid of pin thumbnails, shared by the search and saved-folder screens. */
public class PinGrid {

    private static final int CELL_PADDING = 4;
    private static final int MIN_CELL_WIDTH = 96;

    private final List<PinterestApi.Pin> pins = new ArrayList<>();

    private int left;
    private int top;
    private int right;
    private int bottom;
    private int columns = 1;
    private int cellWidth = MIN_CELL_WIDTH;
    private int cellHeight = MIN_CELL_WIDTH;
    private double scroll;

    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        int available = right - left;
        columns = Math.max(1, available / (MIN_CELL_WIDTH + CELL_PADDING));
        cellWidth = (available - (columns - 1) * CELL_PADDING) / columns;
        cellHeight = Math.round(cellWidth * 1.15F);
    }

    public List<PinterestApi.Pin> pins() {
        return pins;
    }

    public void setPins(List<PinterestApi.Pin> newPins) {
        pins.clear();
        pins.addAll(newPins);
        scroll = 0.0D;
    }

    public void addPins(List<PinterestApi.Pin> newPins) {
        pins.addAll(newPins);
    }

    public void clear() {
        pins.clear();
        scroll = 0.0D;
    }

    /** True once the player has scrolled far enough that the next page should be requested. */
    public boolean isNearEnd() {
        return scroll >= contentHeight() - (bottom - top) - cellHeight;
    }

    public void scrollBy(double amount) {
        scroll = Math.clamp(scroll - amount * (cellHeight / 3.0D), 0.0D, maxScroll());
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        scroll = Math.clamp(scroll, 0.0D, maxScroll());
        guiGraphics.enableScissor(left, top, right, bottom);
        for (int index = 0; index < pins.size(); index++) {
            int cellX = left + (index % columns) * (cellWidth + CELL_PADDING);
            int cellY = top + (index / columns) * (cellHeight + CELL_PADDING) - (int) scroll;
            if (cellY + cellHeight < top || cellY > bottom) {
                continue;
            }
            renderCell(guiGraphics, font, pins.get(index), cellX, cellY, mouseX, mouseY);
        }
        guiGraphics.disableScissor();
        renderScrollbar(guiGraphics, right - 3, top, bottom, scroll, contentHeight());
    }

    /** Draws a thin scroll indicator, or nothing when everything already fits. */
    public static void renderScrollbar(GuiGraphics guiGraphics, int x, int top, int bottom,
                                       double scroll, int contentHeight) {
        int viewHeight = bottom - top;
        if (contentHeight <= viewHeight || viewHeight <= 0) {
            return;
        }
        int thumbHeight = Math.max(16, viewHeight * viewHeight / contentHeight);
        int travel = viewHeight - thumbHeight;
        int thumbTop = top + (int) Math.round(travel * scroll / (contentHeight - viewHeight));
        guiGraphics.fill(x, top, x + 3, bottom, 0x30FFFFFF);
        guiGraphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, PinTheme.ACCENT);
    }

    private void renderCell(GuiGraphics guiGraphics, Font font, PinterestApi.Pin pin,
                            int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight
                && mouseY >= top && mouseY < bottom;
        PinTheme.card(guiGraphics, x, y, cellWidth, cellHeight, hovered);

        ThumbnailCache.Thumbnail thumbnail = ThumbnailCache.get(pin.thumbnailUrl());
        if (thumbnail == null) {
            guiGraphics.drawCenteredString(font, "...", x + cellWidth / 2, y + cellHeight / 2 - 4,
                    PinTheme.TEXT_DISABLED);
            return;
        }
        float fit = Math.min(
                (float) (cellWidth - 2) / thumbnail.width(),
                (float) (cellHeight - 2) / thumbnail.height());
        int drawWidth = Math.max(1, Math.round(thumbnail.width() * fit));
        int drawHeight = Math.max(1, Math.round(thumbnail.height() * fit));
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                thumbnail.texture(),
                x + (cellWidth - drawWidth) / 2,
                y + (cellHeight - drawHeight) / 2,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                thumbnail.width(),
                thumbnail.height(),
                thumbnail.width(),
                thumbnail.height(),
                0xFFFFFFFF);
        if (hovered) {
            guiGraphics.fill(x + 1, y + cellHeight - 12, x + cellWidth - 1, y + cellHeight - 1, 0xD0121216);
            guiGraphics.fill(x + 1, y + cellHeight - 13, x + cellWidth - 1, y + cellHeight - 12, PinTheme.ACCENT);
            guiGraphics.drawString(font, font.plainSubstrByWidth(pin.title(), cellWidth - 8),
                    x + 4, y + cellHeight - 10, PinTheme.TEXT, false);
        }
    }

    @Nullable
    public PinterestApi.Pin pinAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
            return null;
        }
        int column = (int) ((mouseX - left) / (cellWidth + CELL_PADDING));
        int row = (int) ((mouseY - top + scroll) / (cellHeight + CELL_PADDING));
        if (column < 0 || column >= columns) {
            return null;
        }
        int index = row * columns + column;
        return index >= 0 && index < pins.size() ? pins.get(index) : null;
    }

    private int contentHeight() {
        int rows = (pins.size() + columns - 1) / columns;
        return rows * (cellHeight + CELL_PADDING);
    }

    private double maxScroll() {
        return Math.max(0.0D, contentHeight() - (bottom - top));
    }
}
