package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    protected static final int COLOR_TEXT = 0xFFFFFFFF;
    protected static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_BAR = 0xC0101014;
    private static final int COLOR_PANEL = 0x50000000;
    private static final int COLOR_LINE = 0xFF3C3C42;

    public enum Tab {
        SEARCH, SAVED, ACCOUNT, SETTINGS
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
        int tabWidth = Math.min(76, (width - MARGIN * 2) / Tab.values().length - 2);
        int x = MARGIN;
        for (Tab value : Tab.values()) {
            Button button = Button
                    .builder(Component.translatable("tab.pinspo." + value.name().toLowerCase()),
                            ignored -> open(value))
                    .bounds(x, 6, tabWidth, TAB_HEIGHT)
                    .build();
            button.active = value != tab();
            addRenderableWidget(button);
            x += tabWidth + 2;
        }
        addRenderableWidget(Button
                .builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, height - FOOTER_HEIGHT + 6, 80, 20)
                .build());
    }

    private void open(Tab target) {
        if (target == tab()) {
            return;
        }
        minecraft.setScreen(switch (target) {
            case SEARCH -> new PinBrowseScreen(parent);
            case SAVED -> new SavedPinsScreen(parent);
            case ACCOUNT -> new PinAccountScreen(parent);
            case SETTINGS -> new PinSettingsScreen(parent);
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Chrome drawn on top of the vanilla dim: a tab bar, a titled content area and a footer.
        guiGraphics.fill(0, 0, width, TAB_HEIGHT + 12, COLOR_BAR);
        guiGraphics.fill(0, TAB_HEIGHT + 12, width, TAB_HEIGHT + 13, COLOR_LINE);
        guiGraphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, COLOR_LINE);
        guiGraphics.fill(0, height - FOOTER_HEIGHT + 1, width, height, COLOR_BAR);
        guiGraphics.drawString(font, title, MARGIN, TAB_HEIGHT + 20, COLOR_TEXT, false);
    }

    /** Draws a subtle rounded-ish backing panel behind a region of content. */
    protected void renderPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, COLOR_PANEL);
        guiGraphics.fill(left, top, right, top + 1, COLOR_LINE);
        guiGraphics.fill(left, bottom - 1, right, bottom, COLOR_LINE);
        guiGraphics.fill(left, top, left + 1, bottom, COLOR_LINE);
        guiGraphics.fill(right - 1, top, right, bottom, COLOR_LINE);
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
