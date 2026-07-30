package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Pinterest grid: searches through {@link PinterestApi} and draws the results as plain textures,
 * so browsing for a reference costs nothing like the embedded Chromium browser does.
 */
public class PinBrowseScreen extends Screen {

    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 28;
    private static final int CELL_PADDING = 4;
    private static final int MIN_CELL_WIDTH = 96;

    @Nullable
    private final Screen parent;
    private final List<PinterestApi.Pin> pins = new ArrayList<>();

    @Nullable
    private EditBox searchBox;
    private String query = "";
    @Nullable
    private String bookmark;
    private boolean loading;
    private boolean exhausted;
    @Nullable
    private Component error;
    private double scroll;
    private int columns = 1;
    private int cellWidth = MIN_CELL_WIDTH;
    private int cellHeight = MIN_CELL_WIDTH;

    public PinBrowseScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.browse"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int searchWidth = Math.min(240, width - 150);
        searchBox = new EditBox(font, 10, 8, searchWidth, 18,
                Component.translatable("screen.pinspo.search"));
        searchBox.setHint(Component.translatable("screen.pinspo.search_hint"));
        searchBox.setMaxLength(120);
        searchBox.setValue(query);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.search_button"), button -> startSearch())
                .bounds(searchWidth + 16, 8, 60, 18)
                .build());
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.full_browser"),
                        button -> minecraft.setScreen(new PinterestBrowserScreen(this)))
                .bounds(width - 74, 8, 64, 18)
                .build());
        addRenderableWidget(Button
                .builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, height - FOOTER_HEIGHT + 4, 80, 20)
                .build());

        layoutGrid();
    }

    private void layoutGrid() {
        int available = width - 20;
        columns = Math.max(1, available / (MIN_CELL_WIDTH + CELL_PADDING));
        cellWidth = (available - (columns - 1) * CELL_PADDING) / columns;
        cellHeight = Math.round(cellWidth * 1.15F);
    }

    private void startSearch() {
        if (searchBox == null) {
            return;
        }
        String newQuery = searchBox.getValue().trim();
        if (newQuery.isEmpty()) {
            return;
        }
        query = newQuery;
        pins.clear();
        ThumbnailCache.clear();
        bookmark = null;
        exhausted = false;
        error = null;
        scroll = 0.0D;
        loadMore();
    }

    private void loadMore() {
        if (loading || exhausted || query.isEmpty()) {
            return;
        }
        loading = true;
        String requested = query;
        PinterestApi.search(requested, bookmark).whenComplete((page, throwable) -> minecraft.execute(() -> {
            loading = false;
            if (!requested.equals(query)) {
                return;
            }
            if (throwable != null || page == null) {
                error = Component.translatable("screen.pinspo.search_failed");
                exhausted = true;
                PinSpoClient.LOGGER.warn("Pinterest search failed", throwable);
                return;
            }
            pins.addAll(page.pins());
            bookmark = page.bookmark();
            exhausted = bookmark == null || page.pins().isEmpty();
        }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int top = HEADER_HEIGHT;
        int bottom = height - FOOTER_HEIGHT;
        int rows = (pins.size() + columns - 1) / columns;
        int contentHeight = rows * (cellHeight + CELL_PADDING);
        scroll = Math.clamp(scroll, 0.0D, Math.max(0.0D, contentHeight - (bottom - top)));

        guiGraphics.enableScissor(0, top, width, bottom);
        for (int index = 0; index < pins.size(); index++) {
            int cellX = 10 + (index % columns) * (cellWidth + CELL_PADDING);
            int cellY = top + (index / columns) * (cellHeight + CELL_PADDING) - (int) scroll;
            if (cellY + cellHeight < top || cellY > bottom) {
                continue;
            }
            renderCell(guiGraphics, pins.get(index), cellX, cellY, mouseX, mouseY);
        }
        guiGraphics.disableScissor();

        if (pins.isEmpty()) {
            Component message = error != null
                    ? error
                    : loading
                            ? Component.translatable("screen.pinspo.searching")
                            : Component.translatable("screen.pinspo.search_prompt");
            guiGraphics.drawCenteredString(font, message, width / 2, height / 2 - 4, 0xFFAAAAAA);
        } else if (loading) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.searching"),
                    width / 2, bottom - 12, 0xFFAAAAAA);
        }

        // Requesting the next page while the last row is in view keeps scrolling continuous.
        if (!loading && !exhausted && scroll >= contentHeight - (bottom - top) - cellHeight) {
            loadMore();
        }
    }

    private void renderCell(GuiGraphics guiGraphics, PinterestApi.Pin pin, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight;
        guiGraphics.fill(x, y, x + cellWidth, y + cellHeight, hovered ? 0xFF3A3A3A : 0xFF1E1E1E);

        ThumbnailCache.Thumbnail thumbnail = ThumbnailCache.get(pin.thumbnailUrl());
        if (thumbnail == null) {
            guiGraphics.drawCenteredString(font, "...", x + cellWidth / 2, y + cellHeight / 2 - 4, 0xFF777777);
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
            guiGraphics.fill(x, y + cellHeight - 10, x + cellWidth, y + cellHeight, 0xC0000000);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(pin.title(), cellWidth - 6),
                    x + 3, y + cellHeight - 9, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        PinterestApi.Pin pin = pinAt(event.x(), event.y());
        if (pin != null) {
            PinnedImage.pin(pin.imageUrl());
            onClose();
            return true;
        }
        return false;
    }

    @Nullable
    private PinterestApi.Pin pinAt(double mouseX, double mouseY) {
        int top = HEADER_HEIGHT;
        if (mouseY < top || mouseY > height - FOOTER_HEIGHT || mouseX < 10) {
            return null;
        }
        int column = (int) ((mouseX - 10) / (cellWidth + CELL_PADDING));
        int row = (int) ((mouseY - top + scroll) / (cellHeight + CELL_PADDING));
        if (column < 0 || column >= columns) {
            return null;
        }
        int index = row * columns + column;
        return index >= 0 && index < pins.size() ? pins.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * (cellHeight / 3.0D);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && searchBox != null && searchBox.isFocused()) {
            startSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        ThumbnailCache.clear();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
