package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** What can be done with one friend: right-clicking their row in the Friends tab opens this. */
public class PinFriendOptionsScreen extends Screen {

    private static final int WIDGET_WIDTH = 180;

    private final Screen parent;
    private final String friend;
    /** Removing a friend or blocking them throws a conversation away, so both ask first. */
    private boolean confirmingUnfriend;
    private boolean confirmingBlock;

    public PinFriendOptionsScreen(Screen parent, String friend) {
        super(Component.literal(friend));
        this.parent = parent;
        this.friend = friend;
    }

    @Override
    protected void init() {
        int x = (width - WIDGET_WIDTH) / 2;
        int y = Math.max(40, height / 2 - 50);

        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable(confirmingUnfriend
                        ? "screen.pinspo.unfriend_confirm"
                        : "screen.pinspo.unfriend"),
                this::unfriend));
        y += 24;

        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable(confirmingBlock
                        ? "screen.pinspo.block_confirm"
                        : "screen.pinspo.block"),
                this::block));
        y += 24;

        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.clear_chat"), () -> {
                    PinFriends.clearConversation(friend);
                    onClose();
                }));
        y += 28;

        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("gui.cancel"), this::onClose));
    }

    private void unfriend() {
        if (!confirmingUnfriend) {
            confirmingUnfriend = true;
            confirmingBlock = false;
            rebuild();
            return;
        }
        PinFriends.removeFriend(friend);
        onClose();
    }

    private void block() {
        if (!confirmingBlock) {
            confirmingBlock = true;
            confirmingUnfriend = false;
            rebuild();
            return;
        }
        PinFriends.block(friend);
        onClose();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, Math.max(20, height / 2 - 74), PinTheme.TEXT);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.friend_options"),
                width / 2, Math.max(32, height / 2 - 64), PinTheme.TEXT_MUTED);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
