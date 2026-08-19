package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Base for the PinSpo screens, giving them a shared tab bar, header and footer. */
public abstract class PinTabScreen extends Screen {

    protected static final int TAB_HEIGHT = 20;
    /** Everything below the tab bar and the screen title starts here. */
    protected static final int CONTENT_TOP = 56;
    protected static final int FOOTER_HEIGHT = 32;
    protected static final int MARGIN = 12;

    protected static final int COLOR_TEXT = PinTheme.TEXT;
    protected static final int COLOR_MUTED = PinTheme.TEXT_MUTED;

    /** Koidu's koi, drawn next to the wordmark. */
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("pinspo", "textures/gui/koi.png");
    private static final int LOGO_SIZE = 18;
    private static final String CREDIT = "Made by Koidu";

    public enum Tab {
        SEARCH, IMPORT, SAVED, FRIENDS, SETTINGS
    }

    @Nullable
    protected final Screen parent;

    protected PinTabScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract Tab tab();

    /** Adds the tab bar and the Done button; subclasses call this from {@code init}. */
    protected void addTabs() {
        int labelWidth = MARGIN + LOGO_SIZE + 46;
        int tabWidth = Math.max(46,
                Math.min(88, (width - labelWidth - MARGIN) / Tab.values().length - 2));
        int x = labelWidth;
        for (Tab value : Tab.values()) {
            PinButton button = new PinButton(x, 6, tabWidth, TAB_HEIGHT,
                    Component.translatable("tab.pinspo." + value.name().toLowerCase()),
                    PinButton.Style.TAB, () -> open(value));
            button.selected(value == tab());
            addRenderableWidget(button);
            x += tabWidth + 2;
        }
        addRenderableWidget(PinButton.primary(width - MARGIN - 80, height - FOOTER_HEIGHT + 6, 80, 20,
                Component.translatable("gui.done"), this::onClose));
    }

    private void open(Tab target) {
        if (target == tab()) {
            return;
        }
        minecraft.setScreen(switch (target) {
            case SEARCH -> new PinBrowseScreen(parent);
            case IMPORT -> new PinImportScreen(parent);
            case SAVED -> new SavedPinsScreen(parent);
            case FRIENDS -> new PinFriendsScreen(parent);
            case SETTINGS -> new PinSettingsScreen(parent);
        });
    }

    /**
     * The chrome belongs to the background: drawing it in {@code render} put the bar's fill on top of the
     * tab buttons, which is what made them look dimmed.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, width, TAB_HEIGHT + 12, PinTheme.BAR);
        guiGraphics.fill(0, TAB_HEIGHT + 12, width, TAB_HEIGHT + 13, PinTheme.BORDER);
        guiGraphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, PinTheme.BORDER);
        guiGraphics.fill(0, height - FOOTER_HEIGHT + 1, width, height, PinTheme.BAR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        boolean overLogo = overLogo(mouseX, mouseY);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, MARGIN, 7, 0.0F, 0.0F,
                LOGO_SIZE, LOGO_SIZE, 64, 64, 64, 64);
        guiGraphics.drawString(font, Component.literal("PinSpo"), MARGIN + LOGO_SIZE + 3, 12,
                overLogo ? PinTheme.ACCENT : PinTheme.TEXT, false);
        guiGraphics.drawString(font, title, MARGIN, TAB_HEIGHT + 20, COLOR_TEXT, false);
        if (overLogo) {
            // The bar has no room next to the wordmark, so the hint goes in the empty left half of the footer.
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.open_home"),
                    MARGIN, height - FOOTER_HEIGHT + 12, COLOR_MUTED, false);
        }
        renderCredit(guiGraphics);
    }

    /** A half-size signature along the very bottom edge: there if you look for it, quiet if you do not. */
    private void renderCredit(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(0.5F, 0.5F);
        // Halved coordinates, so the line lands in the last few pixels of the bottom-right corner.
        guiGraphics.drawString(font, CREDIT,
                (width - MARGIN) * 2 - font.width(CREDIT), (height - 6) * 2, PinTheme.CREDIT, false);
        guiGraphics.pose().popMatrix();
    }

    /** The wordmark doubles as a home button, so it reacts to the mouse like one. */
    private boolean overLogo(int mouseX, int mouseY) {
        return mouseX >= MARGIN && mouseX <= MARGIN + LOGO_SIZE + 5 + font.width("PinSpo")
                && mouseY >= 6 && mouseY <= 26;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (overLogo((int) event.x(), (int) event.y())) {
            minecraft.setScreen(PinBrowseScreen.home(parent));
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /** Draws a subtle rounded-ish backing panel behind a region of content. */
    protected void renderPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        PinTheme.panel(guiGraphics, left, top, right - left, bottom - top);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Leaving PinSpo entirely: the grid textures are no longer needed.
        ThumbnailCache.clear();
        minecraft.setScreen(parent);
    }
}
