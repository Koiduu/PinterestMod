package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What opens when a reference is right-clicked: a preview of the pin plus two tabs — send it to a friend,
 * or save it into a folder. Replaces the old folder-only dialog so both actions are one click away.
 */
public class PinActionScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int ROW = 22;
    private static final int PREVIEW = 54;
    private static final int VISIBLE_ROWS = 5;

    private enum Mode {
        SEND("screen.pinspo.tab_send"),
        SAVE("screen.pinspo.tab_save");

        private final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private final PinterestApi.Pin pin;
    private Mode mode = Mode.SEND;
    private int scroll;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private Component feedback;
    private int panelTop;
    private int panelHeight;
    private int listTop;

    public PinActionScreen(Screen parent, PinterestApi.Pin pin) {
        super(Component.translatable("screen.pinspo.pin_actions"));
        this.parent = parent;
        this.pin = pin;
    }

    @Override
    protected void init() {
        List<String> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - VISIBLE_ROWS)));

        int x = (width - PANEL_WIDTH) / 2;
        panelHeight = 30 + PREVIEW + 8 + 24 + Math.min(rows.size(), VISIBLE_ROWS) * ROW + 8 + ROW + 24 + 8;
        panelTop = Math.max(24, (height - panelHeight) / 2);
        int y = panelTop + 12 + PREVIEW + 8;

        int tabWidth = (PANEL_WIDTH - 4) / 2;
        for (Mode value : Mode.values()) {
            addRenderableWidget(new PinButton(x + (value.ordinal() * (tabWidth + 4)), y, tabWidth, 20,
                    Component.translatable(value.label), PinButton.Style.TAB, () -> {
                        mode = value;
                        scroll = 0;
                        feedback = null;
                        rebuild();
                    }).selected(mode == value));
        }
        y += 24;

        listTop = y;
        List<String> visible = rows.subList(
                Math.min(scroll, rows.size()),
                Math.min(scroll + VISIBLE_ROWS, rows.size()));
        for (String row : visible) {
            addRenderableWidget(PinButton.of(x, y, PANEL_WIDTH, 20, Component.literal(row),
                    () -> choose(row)));
            y += ROW;
        }
        if (rows.isEmpty()) {
            y += ROW;
        }
        y += 8;

        nameBox = new EditBox(font, x, y, PANEL_WIDTH - 52, 18, Component.translatable(
                mode == Mode.SEND ? "screen.pinspo.friend_name" : "screen.pinspo.new_folder"));
        nameBox.setHint(Component.translatable(
                mode == Mode.SEND ? "screen.pinspo.friend_hint" : "screen.pinspo.folder_hint"));
        nameBox.setMaxLength(40);
        addRenderableWidget(nameBox);
        addRenderableWidget(PinButton.primary(x + PANEL_WIDTH - 48, y, 48, 18,
                Component.translatable("screen.pinspo.add"), this::addRow));
        y += ROW + 2;

        addRenderableWidget(PinButton.of(x, y, PANEL_WIDTH / 2 - 2, 20,
                Component.translatable("screen.pinspo.copy_code"), this::copyCode));
        addRenderableWidget(PinButton.of(x + PANEL_WIDTH / 2 + 2, y, PANEL_WIDTH / 2 - 2, 20,
                Component.translatable("gui.done"), this::onClose));
        panelHeight = y + 20 + 10 - panelTop;
    }

    private List<String> rows() {
        return mode == Mode.SEND ? PinFriends.friends() : List.copyOf(SavedPins.folderNames());
    }

    /** Sends to the clicked friend, or saves into the clicked folder. */
    private void choose(String row) {
        if (mode == Mode.SAVE) {
            SavedPins.add(row, pin);
            onClose();
            return;
        }
        if (!PinShare.isShareable(pin)) {
            feedback = Component.translatable("screen.pinspo.not_shareable");
            return;
        }
        if (PinChat.sendPin(row, pin)) {
            feedback = Component.translatable("screen.pinspo.sent_to", row);
            rebuild();
            return;
        }
        feedback = Component.translatable("screen.pinspo.send_failed");
    }

    /** Adds a friend, or creates a folder, and immediately acts on it. */
    private void addRow() {
        if (nameBox == null || nameBox.getValue().isBlank()) {
            return;
        }
        String value = nameBox.getValue().trim();
        if (mode == Mode.SAVE) {
            String folder = SavedPins.createFolder(value);
            if (!folder.isEmpty()) {
                SavedPins.add(folder, pin);
                onClose();
            }
            return;
        }
        if (!PinSecurity.isPlayerName(value)) {
            feedback = Component.translatable("screen.pinspo.bad_name");
            return;
        }
        // Friends are only added through a request, so this invites them instead.
        boolean delivered = PinChat.sendRequest(value);
        nameBox.setValue("");
        feedback = Component.translatable(delivered
                ? "screen.pinspo.request_sent"
                : "screen.pinspo.request_offline", value);
        rebuild();
    }

    /** Clipboard fallback for servers that do not allow private messages. */
    private void copyCode() {
        if (!PinShare.isShareable(pin)) {
            feedback = Component.translatable("screen.pinspo.not_shareable");
            return;
        }
        minecraft.keyboardHandler.setClipboard(PinShare.encode(PinFriends.selfName(), List.of(pin)));
        feedback = Component.translatable("screen.pinspo.send_copied");
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rows = rows().size();
        if (rows > VISIBLE_ROWS) {
            scroll = Math.max(0, Math.min(rows - VISIBLE_ROWS, scroll - (int) Math.signum(verticalAmount)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        int x = (width - PANEL_WIDTH) / 2;
        PinTheme.panel(guiGraphics, x - 10, panelTop, PANEL_WIDTH + 20, panelHeight);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, title, x, panelTop + 6, PinTheme.ACCENT, false);
        renderPreview(guiGraphics, x, panelTop + 18);

        if (rows().isEmpty()) {
            guiGraphics.drawString(font,
                    Component.translatable(mode == Mode.SEND
                            ? "screen.pinspo.no_friends_yet"
                            : "screen.pinspo.no_folders_yet"),
                    x, listTop + 6, PinTheme.TEXT_MUTED, false);
        }
        if (feedback != null) {
            guiGraphics.drawString(font, feedback, x, panelTop + panelHeight - 10,
                    PinTheme.TEXT_MUTED, false);
        }
    }

    /** The reference itself, so it is obvious which pin is being sent or saved. */
    private void renderPreview(GuiGraphics guiGraphics, int x, int y) {
        ThumbnailCache.Thumbnail thumbnail = ThumbnailCache.get(pin.thumbnailUrl());
        PinTheme.card(guiGraphics, x, y, PREVIEW, PREVIEW, false);
        if (thumbnail != null) {
            float fit = Math.min(
                    (float) (PREVIEW - 2) / thumbnail.width(),
                    (float) (PREVIEW - 2) / thumbnail.height());
            int drawWidth = Math.max(1, Math.round(thumbnail.width() * fit));
            int drawHeight = Math.max(1, Math.round(thumbnail.height() * fit));
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    thumbnail.texture(),
                    x + (PREVIEW - drawWidth) / 2,
                    y + (PREVIEW - drawHeight) / 2,
                    0.0F,
                    0.0F,
                    drawWidth,
                    drawHeight,
                    thumbnail.width(),
                    thumbnail.height(),
                    thumbnail.width(),
                    thumbnail.height(),
                    0xFFFFFFFF);
        }
        String label = pin.title().isBlank()
                ? Component.translatable("screen.pinspo.reference").getString()
                : pin.title();
        guiGraphics.drawString(font, font.plainSubstrByWidth(label, PANEL_WIDTH - PREVIEW - 8),
                x + PREVIEW + 8, y + 4, PinTheme.TEXT, false);
        guiGraphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable("screen.pinspo.pin_actions_hint").getString(),
                        PANEL_WIDTH - PREVIEW - 8),
                x + PREVIEW + 8, y + 18, PinTheme.TEXT_MUTED, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
