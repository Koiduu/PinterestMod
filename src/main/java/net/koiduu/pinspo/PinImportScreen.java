package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * Images that do not come from Pinterest: a file picked from disk, a pasted image link, or one of the
 * player's own screenshots. Everything ends up as a normal pin, so it can be overlaid or saved like any
 * search result.
 */
public class PinImportScreen extends PinTabScreen {

    private final PinGrid grid = new PinGrid();

    @Nullable
    private EditBox linkBox;
    @Nullable
    private Component feedback;

    public PinImportScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.import"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.IMPORT;
    }

    @Override
    protected void init() {
        addTabs();

        int buttonsWidth = 168;
        int linkWidth = Math.max(120, width - MARGIN * 2 - buttonsWidth - 12);
        linkBox = new EditBox(font, MARGIN, CONTENT_TOP, linkWidth, 20,
                Component.translatable("screen.pinspo.link"));
        linkBox.setHint(Component.translatable("screen.pinspo.link_hint"));
        linkBox.setMaxLength(512);
        addRenderableWidget(linkBox);
        setInitialFocus(linkBox);

        int x = MARGIN + linkWidth + 6;
        addRenderableWidget(PinButton.primary(x, CONTENT_TOP, 60, 20,
                Component.translatable("screen.pinspo.pin_link"), this::pinLink));
        addRenderableWidget(PinButton.of(x + 64, CONTENT_TOP, 44, 20,
                Component.translatable("screen.pinspo.paste"), this::pasteLink));
        addRenderableWidget(PinButton.of(x + 112, CONTENT_TOP, 56, 20,
                Component.translatable("screen.pinspo.choose_file"), this::chooseFile));

        grid.setPins(LocalImages.browsable());
        grid.setBounds(MARGIN, CONTENT_TOP + 38, width - MARGIN, height - FOOTER_HEIGHT - 8);
    }

    private void pasteLink() {
        if (linkBox != null) {
            linkBox.setValue(minecraft.keyboardHandler.getClipboard().trim());
            pinLink();
        }
    }

    /** Pins a pasted image address; Pinterest pin pages are resolved to their image first. */
    private void pinLink() {
        if (linkBox == null) {
            return;
        }
        String url = PinSecurity.withoutImageConversion(linkBox.getValue().trim());
        if (url.isEmpty()) {
            return;
        }
        if (PinSecurity.isAllowedImageUrl(url)) {
            PinnedImage.pin(new PinterestApi.Pin(url,
                    Component.translatable("screen.pinspo.pasted_image").getString(), url, url, 0, 0));
            onClose();
            return;
        }
        if (url.startsWith("https://www.pinterest.com/pin/") || url.startsWith("https://pin.it/")) {
            PinnedImage.pin(url);
            onClose();
            return;
        }
        feedback = Component.translatable("screen.pinspo.bad_link");
    }

    /**
     * Opens the operating system's own file chooser. It blocks, so it runs on its own thread and the
     * result is handed back to the client thread.
     */
    private void chooseFile() {
        feedback = Component.translatable("screen.pinspo.choosing_file");
        Util.nonCriticalIoPool().execute(() -> {
            String chosen = openDialog();
            minecraft.execute(() -> {
                if (chosen == null) {
                    feedback = null;
                    return;
                }
                PinterestApi.Pin pin = LocalImages.importFile(Path.of(chosen));
                if (pin == null) {
                    feedback = Component.translatable("screen.pinspo.bad_file");
                    return;
                }
                PinnedImage.pin(pin);
                onClose();
            });
        });
    }

    @Nullable
    private static String openDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                    .put(stack.UTF8("*.webp")).put(stack.UTF8("*.gif"));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("screen.pinspo.choose_file_title").getString(),
                    null, filters, "Images", false);
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not open the system file chooser", e);
            return null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        grid.render(guiGraphics, font, mouseX, mouseY);

        Component hint = feedback != null
                ? feedback
                : Component.translatable(grid.pins().isEmpty()
                        ? "screen.pinspo.no_local_images"
                        : "screen.pinspo.local_hint");
        guiGraphics.drawString(font, font.plainSubstrByWidth(hint.getString(), width - MARGIN * 2),
                MARGIN, CONTENT_TOP + 24, feedback != null ? PinTheme.ACCENT : COLOR_MUTED, false);
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
            minecraft.setScreen(new PinActionScreen(this, pin));
            return true;
        }
        PinnedImage.pin(pin);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        grid.scrollBy(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && linkBox != null && linkBox.isFocused()) {
            pinLink();
            return true;
        }
        return super.keyPressed(event);
    }
}
