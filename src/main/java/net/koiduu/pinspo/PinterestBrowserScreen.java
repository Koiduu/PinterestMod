package net.koiduu.pinspo;

import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class PinterestBrowserScreen extends Screen {

    @Nullable
    private final Screen parent;
    @Nullable
    private MCEFBrowser browser;
    private int browserWidth;
    private int browserHeight;
    /** Panel bounds in GUI coordinates; the browser is drawn as a window rather than fullscreen. */
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public PinterestBrowserScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.browser"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BrowserHolder.api();
        acquireBrowser();
    }

    /**
     * Ratio between Chromium's render surface and the on-screen panel in GUI coordinates. Chromium
     * paints at framebuffer resolution, but the surface is additionally capped by
     * {@code maxBrowserWidth} for performance, so this is not simply the GUI scale.
     */
    private float scale() {
        return panelWidth > 0 ? (float) browserWidth / panelWidth : 1.0F;
    }

    private void acquireBrowser() {
        float windowScale = PinSpoConfig.get().browserWindowScale;
        panelWidth = Math.max(160, Math.round(width * windowScale));
        panelHeight = Math.max(120, Math.round(height * windowScale));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int framebufferWidth = panelWidth * Math.max(1, minecraft.getWindow().getGuiScale());
        int pixelWidth = BrowserHolder.renderWidth(framebufferWidth);
        int pixelHeight = Math.max(1, Math.round(panelHeight * (float) pixelWidth / panelWidth));
        if (browser == null) {
            browser = BrowserHolder.browserIfReady(pixelWidth, pixelHeight);
        }
        if (browser == null) {
            return;
        }
        if (browserWidth != pixelWidth || browserHeight != pixelHeight) {
            browser.resize(pixelWidth, pixelHeight);
            browserWidth = pixelWidth;
            browserHeight = pixelHeight;
        }
        browser.setFocus(true);
        BrowserHolder.setVisible(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        acquireBrowser();
        if (browser != null && BrowserHolder.updateTexture()) {
            guiGraphics.fill(0, 0, width, height, 0xB0000000);
            guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFFE60023);
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    BrowserHolder.BROWSER_TEXTURE_ID,
                    panelX,
                    panelY,
                    0.0F,
                    0.0F,
                    panelWidth,
                    panelHeight,
                    browserWidth,
                    browserHeight,
                    browserWidth,
                    browserHeight,
                    0xFFFFFFFF
            );
            guiGraphics.requestCursor(browser.getCursorType());
        } else {
            // Not renderBackground(): the vanilla screen render already blurred this frame,
            // and blurring twice per frame throws.
            guiGraphics.fill(0, 0, width, height, 0xC0101010);
            guiGraphics.drawCenteredString(font, statusMessage(), width / 2, height / 2 - 4, 0xFFFFFFFF);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private Component statusMessage() {
        MCEFApi.Initialization.Stage stage = BrowserHolder.initStage();
        float percentage = BrowserHolder.initPercentage();
        Component stageName = Component.translatable("screen.pinspo.stage." + stage.name().toLowerCase());
        return percentage >= 0.0F
                ? Component.translatable("screen.pinspo.loading_percent", stageName, (int) percentage)
                : Component.translatable("screen.pinspo.loading", stageName);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (browser == null) {
            return super.mouseClicked(event, doubled);
        }
        if (!isInPanel(event.x(), event.y())) {
            // Clicking the dimmed backdrop closes the window, like a vanilla popup.
            onClose();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.hasShiftDown()) {
            pinImageAt((int) Math.round(browserX(event.x())), (int) Math.round(browserY(event.y())));
            return true;
        }
        browser.onMouseClicked(toBrowserSpace(event), doubled);
        return true;
    }

    private MouseButtonEvent toBrowserSpace(MouseButtonEvent event) {
        return new MouseButtonEvent(browserX(event.x()), browserY(event.y()), event.buttonInfo());
    }

    private double browserX(double guiX) {
        return (guiX - panelX) * scale();
    }

    private double browserY(double guiY) {
        return (guiY - panelY) * scale();
    }

    private boolean isInPanel(double guiX, double guiY) {
        return guiX >= panelX && guiX < panelX + panelWidth
                && guiY >= panelY && guiY < panelY + panelHeight;
    }

    private void pinImageAt(int x, int y) {
        MCEFBrowser current = browser;
        if (current == null) {
            return;
        }
        ImageResolver.resolveAt(current, x, y).thenAccept(url -> minecraft.execute(() -> {
            if (url.isEmpty()) {
                PinSpoClient.LOGGER.debug("No image found at ({}, {})", x, y);
                return;
            }
            PinnedImage.pin(url.get());
            if (minecraft.screen == this) {
                onClose();
            }
        }));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null && !(event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.hasShiftDown())) {
            browser.onMouseReleased(toBrowserSpace(event));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            browser.onMouseScrolled(
                    (int) Math.round(browserX(mouseX)),
                    (int) Math.round(browserY(mouseY)),
                    verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) {
            browser.onMouseMoved((int) Math.round(browserX(x)), (int) Math.round(browserY(y)));
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (browser != null) {
            browser.onKeyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) {
            browser.onKeyReleased(event);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser != null) {
            browser.onCharTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.setFocus(false);
        }
        BrowserHolder.setVisible(false);
        browser = null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
