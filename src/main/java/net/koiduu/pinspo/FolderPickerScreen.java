package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Small dialog for saving a pin into a folder, and for creating folders. */
public class FolderPickerScreen extends Screen {

    private static final int WIDGET_WIDTH = 180;

    private final Screen parent;
    /** The pin being saved, or {@code null} when the screen is only used to create a folder. */
    @Nullable
    private final PinterestApi.Pin pin;

    @Nullable
    private EditBox nameBox;

    public FolderPickerScreen(Screen parent, @Nullable PinterestApi.Pin pin) {
        super(Component.translatable(pin == null ? "screen.pinspo.new_folder" : "screen.pinspo.save_to"));
        this.parent = parent;
        this.pin = pin;
    }

    @Override
    protected void init() {
        int x = (width - WIDGET_WIDTH) / 2;
        int y = Math.max(40, height / 2 - 60);

        if (pin != null) {
            for (String folder : List.copyOf(SavedPins.folderNames())) {
                addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20, Component.literal(folder), () -> {
                    SavedPins.add(folder, pin);
                    onClose();
                }));
                y += 22;
            }
            y += 8;
        }

        nameBox = new EditBox(font, x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.new_folder"));
        nameBox.setHint(Component.translatable("screen.pinspo.folder_hint"));
        nameBox.setMaxLength(40);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        y += 24;

        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.create_folder"), this::createFolder));
        y += 24;

        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("gui.cancel"), this::onClose));
    }

    private void createFolder() {
        if (nameBox == null || nameBox.getValue().isBlank()) {
            return;
        }
        String folder = nameBox.getValue().trim();
        SavedPins.createFolder(folder);
        if (pin != null) {
            SavedPins.add(folder, pin);
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, Math.max(20, height / 2 - 80), 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
