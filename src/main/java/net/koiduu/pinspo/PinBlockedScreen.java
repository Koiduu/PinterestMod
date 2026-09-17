package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The block list, with an Unblock button beside every name. */
public class PinBlockedScreen extends Screen {

    private static final int WIDGET_WIDTH = 200;
    private static final int UNBLOCK_WIDTH = 60;
    /** Long lists are trimmed rather than scrolled: blocking a hundred players is not a real case. */
    private static final int MAX_SHOWN = 8;

    private final Screen parent;
    private List<String> blocked = List.of();

    public PinBlockedScreen(Screen parent) {
        super(Component.translatable("screen.pinspo.blocked"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        blocked = PinFriends.blocked();
        int x = (width - WIDGET_WIDTH) / 2;
        int y = Math.max(40, height / 2 - Math.min(blocked.size(), MAX_SHOWN) * 11 - 20);

        for (String name : blocked.subList(0, Math.min(blocked.size(), MAX_SHOWN))) {
            addRenderableWidget(PinButton.of(x + WIDGET_WIDTH - UNBLOCK_WIDTH, y, UNBLOCK_WIDTH, 20,
                    Component.translatable("screen.pinspo.unblock"), () -> {
                        PinFriends.unblock(name);
                        clearWidgets();
                        init();
                    }));
            y += 22;
        }
        y += 8;
        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("gui.done"), this::onClose));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int x = (width - WIDGET_WIDTH) / 2;
        int y = Math.max(40, height / 2 - Math.min(blocked.size(), MAX_SHOWN) * 11 - 20);
        guiGraphics.drawCenteredString(font, title, width / 2, Math.max(20, y - 20), PinTheme.TEXT);
        if (blocked.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.no_blocked"),
                    width / 2, y, PinTheme.TEXT_MUTED);
            return;
        }
        for (String name : blocked.subList(0, Math.min(blocked.size(), MAX_SHOWN))) {
            guiGraphics.drawString(font, font.plainSubstrByWidth(name, WIDGET_WIDTH - UNBLOCK_WIDTH - 8),
                    x + 2, y + 6, PinTheme.TEXT, false);
            y += 22;
        }
        if (blocked.size() > MAX_SHOWN) {
            guiGraphics.drawString(font,
                    Component.translatable("screen.pinspo.more_blocked", blocked.size() - MAX_SHOWN),
                    x + 2, y + 2, PinTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
