package net.koiduu.pinspo;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Base for the PinSpo screens, giving them a shared Search / Saved / Account tab bar. */
public abstract class PinTabScreen extends Screen {

    protected static final int TAB_HEIGHT = 18;
    protected static final int FOOTER_HEIGHT = 28;

    public enum Tab {
        SEARCH, SAVED, ACCOUNT
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
        int tabWidth = 62;
        int x = 10;
        for (Tab value : Tab.values()) {
            Button button = Button
                    .builder(Component.translatable("tab.pinspo." + value.name().toLowerCase()),
                            ignored -> open(value))
                    .bounds(x, 4, tabWidth, TAB_HEIGHT)
                    .build();
            button.active = value != tab();
            addRenderableWidget(button);
            x += tabWidth + 2;
        }
        addRenderableWidget(Button
                .builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, height - FOOTER_HEIGHT + 4, 80, 20)
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
        });
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
