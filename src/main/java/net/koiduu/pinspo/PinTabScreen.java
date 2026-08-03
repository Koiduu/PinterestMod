package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    public enum Tab {
        SEARCH, SAVED, FRIENDS, ACCOUNT, SETTINGS
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
        int tabWidth = Math.min(76, (width - MARGIN * 2 - 60) / Tab.values().length - 2);
        int x = MARGIN + 58;
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
            case SAVED -> new SavedPinsScreen(parent);
            case FRIENDS -> new PinFriendsScreen(parent);
            case ACCOUNT -> new PinAccountScreen(parent);
            case SETTINGS -> new PinSettingsScreen(parent);
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Chrome drawn on top of the vanilla dim: a branded tab bar, a titled content area and a footer.
        guiGraphics.fill(0, 0, width, TAB_HEIGHT + 12, PinTheme.BAR);
        guiGraphics.fill(0, TAB_HEIGHT + 12, width, TAB_HEIGHT + 13, PinTheme.BORDER);
        guiGraphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, PinTheme.BORDER);
        guiGraphics.fill(0, height - FOOTER_HEIGHT + 1, width, height, PinTheme.BAR);

        guiGraphics.fill(MARGIN, 8, MARGIN + 3, 24, PinTheme.ACCENT);
        guiGraphics.drawString(font, Component.literal("PinSpo"), MARGIN + 8, 12, PinTheme.TEXT, false);
        guiGraphics.drawString(font, title, MARGIN, TAB_HEIGHT + 20, COLOR_TEXT, false);
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
