package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The player's own reference folders: pick a folder on the left, its saved pins fill the grid. */
public class SavedPinsScreen extends PinTabScreen {

    private static final int HEADER_HEIGHT = 52;
    private static final int SIDEBAR_WIDTH = 90;

    private final PinGrid grid = new PinGrid();

    private String folder = SavedPins.DEFAULT_FOLDER;

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

        List<String> names = SavedPins.folderNames();
        if (!names.contains(folder) && !names.isEmpty()) {
            folder = names.getFirst();
        }
        int y = HEADER_HEIGHT;
        for (String name : names) {
            Button button = Button
                    .builder(Component.literal(name), ignored -> selectFolder(name))
                    .bounds(10, y, SIDEBAR_WIDTH, 18)
                    .build();
            button.active = !name.equals(folder);
            addRenderableWidget(button);
            y += 20;
        }
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.new_folder"),
                        button -> minecraft.setScreen(new FolderPickerScreen(this, null)))
                .bounds(10, 28, SIDEBAR_WIDTH, 18)
                .build());
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.delete_folder"), button -> {
                    SavedPins.deleteFolder(folder);
                    folder = SavedPins.folderNames().stream().findFirst().orElse(SavedPins.DEFAULT_FOLDER);
                    rebuild();
                })
                .bounds(SIDEBAR_WIDTH + 16, 28, 80, 18)
                .build());

        grid.setBounds(SIDEBAR_WIDTH + 16, HEADER_HEIGHT, width - 10, height - FOOTER_HEIGHT);
        grid.setPins(SavedPins.pins(folder));
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        grid.render(guiGraphics, font, mouseX, mouseY);
        if (grid.pins().isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.no_saved"),
                    (width + SIDEBAR_WIDTH) / 2, height / 2 - 4, 0xFFAAAAAA);
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.remove_hint"),
                    SIDEBAR_WIDTH + 16, HEADER_HEIGHT - 12, 0xFF808080, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        PinterestApi.Pin pin = grid.pinAt(event.x(), event.y());
        if (pin == null) {
            return false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            SavedPins.remove(folder, pin);
            grid.setPins(SavedPins.pins(folder));
            return true;
        }
        PinnedImage.pin(pin.imageUrl());
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        grid.scrollBy(verticalAmount);
        return true;
    }
}
