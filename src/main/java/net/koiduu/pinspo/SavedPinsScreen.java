package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The player's own reference folders: pick a folder on the left, its saved pins fill the grid. */
public class SavedPinsScreen extends PinTabScreen {

    private static final int SIDEBAR_WIDTH = 116;
    private static final int ROW_HEIGHT = 26;
    private static final int COVER_SIZE = 22;
    /** Pseudo-folder backed by {@link PinHistory} rather than by a saved folder. */
    private static final String RECENT = "\u0000recent";

    private final PinGrid grid = new PinGrid();
    private final List<String> folders = new ArrayList<>();

    private String folder = RECENT;
    private double sidebarScroll;
    private int sidebarLeft;
    private int sidebarTop;
    private int sidebarBottom;
    @Nullable
    private Component feedback;

    public SavedPinsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.saved"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.SAVED;
    }

    @Override
    protected void init() {
        addTabs();

        folders.clear();
        folders.add(RECENT);
        folders.addAll(SavedPins.folderNames());
        if (!folders.contains(folder)) {
            folder = folders.getFirst();
        }

        sidebarLeft = MARGIN;
        sidebarTop = CONTENT_TOP + 48;
        sidebarBottom = height - FOOTER_HEIGHT - 8;

        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.new_folder"),
                        button -> minecraft.setScreen(new FolderPickerScreen(this, null)))
                .bounds(sidebarLeft, CONTENT_TOP, SIDEBAR_WIDTH, 20)
                .build());
        Button delete = Button
                .builder(Component.translatable("screen.pinspo.delete_folder"), button -> {
                    SavedPins.deleteFolder(folder);
                    folder = RECENT;
                    rebuild();
                })
                .bounds(sidebarLeft, CONTENT_TOP + 22, SIDEBAR_WIDTH, 20)
                .build();
        delete.active = !isRecent();
        addRenderableWidget(delete);

        int actionsX = sidebarLeft + SIDEBAR_WIDTH + 8;
        Button share = Button
                .builder(Component.translatable("screen.pinspo.share_folder"), button -> shareFolder())
                .bounds(actionsX, CONTENT_TOP, 96, 20)
                .build();
        share.active = !pinsInFolder().isEmpty();
        addRenderableWidget(share);
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.import_code"), button -> importCode())
                .bounds(actionsX + 100, CONTENT_TOP, 96, 20)
                .build());

        grid.setBounds(actionsX, CONTENT_TOP + 48, width - MARGIN, height - FOOTER_HEIGHT - 8);
        grid.setPins(pinsInFolder());
    }

    private boolean isRecent() {
        return RECENT.equals(folder);
    }

    private List<PinterestApi.Pin> pinsInFolder() {
        return isRecent() ? PinHistory.entries() : SavedPins.pins(folder);
    }

    private Component folderLabel(String name) {
        return RECENT.equals(name) ? Component.translatable("screen.pinspo.recent") : Component.literal(name);
    }

    private void shareFolder() {
        List<PinterestApi.Pin> pins = pinsInFolder();
        if (pins.isEmpty()) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(
                PinShare.encode(isRecent() ? "Shared" : folder, pins));
        feedback = Component.translatable("screen.pinspo.share_copied", pins.size());
    }

    private void importCode() {
        String imported = PinShare.importCode(minecraft.keyboardHandler.getClipboard());
        if (imported == null) {
            feedback = Component.translatable("screen.pinspo.import_failed");
            return;
        }
        folder = imported;
        feedback = Component.translatable("screen.pinspo.import_done", imported);
        rebuild();
    }

    private void selectFolder(String name) {
        folder = name;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(guiGraphics, sidebarLeft - 4, sidebarTop - 4,
                sidebarLeft + SIDEBAR_WIDTH + 4, sidebarBottom + 4);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderSidebar(guiGraphics, mouseX, mouseY);
        grid.render(guiGraphics, font, mouseX, mouseY);

        if (grid.pins().isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.no_saved"),
                    (width + SIDEBAR_WIDTH) / 2, height / 2 - 4, COLOR_MUTED);
        }
        Component hint = feedback != null
                ? feedback
                : Component.translatable(isRecent() ? "screen.pinspo.recent_hint" : "screen.pinspo.remove_hint");
        guiGraphics.drawString(font, hint, sidebarLeft + SIDEBAR_WIDTH + 8, CONTENT_TOP + 30,
                COLOR_MUTED, false);
    }

    private void renderSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentHeight = folders.size() * ROW_HEIGHT;
        sidebarScroll = Math.clamp(sidebarScroll, 0.0D,
                Math.max(0.0D, contentHeight - (sidebarBottom - sidebarTop)));
        guiGraphics.enableScissor(sidebarLeft, sidebarTop, sidebarLeft + SIDEBAR_WIDTH, sidebarBottom);
        for (int index = 0; index < folders.size(); index++) {
            String name = folders.get(index);
            int y = sidebarTop + index * ROW_HEIGHT - (int) sidebarScroll;
            if (y + ROW_HEIGHT < sidebarTop || y > sidebarBottom) {
                continue;
            }
            boolean selected = name.equals(folder);
            boolean hovered = mouseX >= sidebarLeft && mouseX < sidebarLeft + SIDEBAR_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY >= sidebarTop && mouseY < sidebarBottom;
            guiGraphics.fill(sidebarLeft, y, sidebarLeft + SIDEBAR_WIDTH, y + ROW_HEIGHT - 2,
                    selected ? 0xFF4A4A52 : hovered ? 0xFF2E2E34 : 0xFF1E1E22);
            renderCover(guiGraphics, name, sidebarLeft + 2, y + 1);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(folderLabel(name).getString(), SIDEBAR_WIDTH - COVER_SIZE - 10),
                    sidebarLeft + COVER_SIZE + 6, y + ROW_HEIGHT / 2 - 6, COLOR_TEXT, false);
        }
        guiGraphics.disableScissor();
        PinGrid.renderScrollbar(guiGraphics, sidebarLeft + SIDEBAR_WIDTH - 3, sidebarTop, sidebarBottom,
                sidebarScroll, contentHeight);
    }

    private void renderCover(GuiGraphics guiGraphics, String name, int x, int y) {
        guiGraphics.fill(x, y, x + COVER_SIZE, y + COVER_SIZE, 0xFF101014);
        List<PinterestApi.Pin> pins = RECENT.equals(name) ? PinHistory.entries() : SavedPins.pins(name);
        if (pins.isEmpty()) {
            return;
        }
        ThumbnailCache.Thumbnail cover = ThumbnailCache.get(pins.getFirst().thumbnailUrl());
        if (cover == null) {
            return;
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, cover.texture(), x, y, 0.0F, 0.0F,
                COVER_SIZE, COVER_SIZE, cover.width(), cover.height(), cover.width(), cover.height(),
                0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (event.x() >= sidebarLeft && event.x() < sidebarLeft + SIDEBAR_WIDTH
                && event.y() >= sidebarTop && event.y() < sidebarBottom) {
            int index = (int) ((event.y() - sidebarTop + sidebarScroll) / ROW_HEIGHT);
            if (index >= 0 && index < folders.size()) {
                feedback = null;
                selectFolder(folders.get(index));
            }
            return true;
        }
        PinterestApi.Pin pin = grid.pinAt(event.x(), event.y());
        if (pin == null) {
            return false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isRecent()) {
                minecraft.setScreen(new FolderPickerScreen(this, pin));
                return true;
            }
            SavedPins.remove(folder, pin);
            grid.setPins(pinsInFolder());
            return true;
        }
        PinnedImage.pin(pin);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= sidebarLeft && mouseX < sidebarLeft + SIDEBAR_WIDTH) {
            sidebarScroll -= verticalAmount * ROW_HEIGHT;
            return true;
        }
        grid.scrollBy(verticalAmount);
        return true;
    }
}
