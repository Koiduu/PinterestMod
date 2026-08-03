package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Friends tab: a friend list on the left and an inbox of received references on the right. Sending copies
 * a share code to the clipboard; receiving reads one from it.
 */
public class PinFriendsScreen extends PinTabScreen {

    private static final int SIDEBAR_WIDTH = 116;
    private static final int ROW_HEIGHT = 20;
    private static final int MESSAGE_HEIGHT = 34;
    private static final int THUMB_SIZE = 30;

    private List<String> friends = List.of();
    private List<PinFriends.Message> inbox = List.of();

    @Nullable
    private EditBox nameBox;
    @Nullable
    private Component feedback;
    private double inboxScroll;
    private int listTop;
    private int listBottom;
    private int inboxLeft;

    public PinFriendsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.friends"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.FRIENDS;
    }

    @Override
    protected void init() {
        friends = PinFriends.friends();
        inbox = PinFriends.inbox();
        listTop = CONTENT_TOP + 26;
        listBottom = height - FOOTER_HEIGHT - 8;
        inboxLeft = MARGIN + SIDEBAR_WIDTH + 12;

        addTabs();

        nameBox = new EditBox(font, MARGIN, CONTENT_TOP, SIDEBAR_WIDTH - 26, 20,
                Component.translatable("screen.pinspo.friend_name"));
        nameBox.setHint(Component.translatable("screen.pinspo.friend_hint"));
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        addRenderableWidget(PinButton.primary(MARGIN + SIDEBAR_WIDTH - 22, CONTENT_TOP, 22, 20,
                Component.literal("+"), this::addFriend));

        addRenderableWidget(PinButton.primary(inboxLeft, CONTENT_TOP, 110, 20,
                Component.translatable("screen.pinspo.receive_code"), this::receiveCode));
        PinButton send = PinButton.of(inboxLeft + 114, CONTENT_TOP, 120, 20,
                Component.translatable("screen.pinspo.send_pinned"), this::sendPinned);
        send.active = PinnedImage.isPinned();
        addRenderableWidget(send);
        PinButton clear = PinButton.of(inboxLeft + 238, CONTENT_TOP, 70, 20,
                Component.translatable("screen.pinspo.clear_inbox"), () -> {
                    PinFriends.clearInbox();
                    feedback = null;
                    rebuild();
                });
        clear.active = !inbox.isEmpty();
        addRenderableWidget(clear);
    }

    private void addFriend() {
        if (nameBox == null || nameBox.getValue().isBlank()) {
            return;
        }
        PinFriends.addFriend(nameBox.getValue());
        nameBox.setValue("");
        rebuild();
    }

    /** Copies the currently pinned reference as a share code for a friend to paste. */
    private void sendPinned() {
        PinterestApi.Pin pin = PinHistory.entries().isEmpty() ? null : PinHistory.entries().getFirst();
        if (pin == null) {
            feedback = Component.translatable("screen.pinspo.nothing_to_send");
            return;
        }
        minecraft.keyboardHandler.setClipboard(PinShare.encode(PinFriends.senderName(), List.of(pin)));
        feedback = Component.translatable("screen.pinspo.send_copied");
    }

    private void receiveCode() {
        int received = PinFriends.receive(minecraft.keyboardHandler.getClipboard());
        if (received == 0) {
            feedback = Component.translatable("screen.pinspo.import_failed");
            return;
        }
        feedback = Component.translatable("screen.pinspo.received", received);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        PinTheme.panel(guiGraphics, MARGIN - 4, listTop - 4, SIDEBAR_WIDTH + 8, listBottom - listTop + 8);
        PinTheme.panel(guiGraphics, inboxLeft - 4, listTop - 4,
                width - MARGIN - inboxLeft + 8, listBottom - listTop + 8);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.friends"),
                MARGIN, listTop - 16);
        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.inbox"),
                inboxLeft, listTop - 16);

        renderFriends(guiGraphics, mouseX, mouseY);
        renderInbox(guiGraphics, mouseX, mouseY);

        Component hint = feedback != null ? feedback : Component.translatable("screen.pinspo.friends_hint");
        guiGraphics.drawString(font, hint, MARGIN, height - FOOTER_HEIGHT + 12, PinTheme.TEXT_MUTED, false);
    }

    private void renderFriends(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (friends.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.no_friends"),
                    MARGIN + 2, listTop + 4, PinTheme.TEXT_MUTED, false);
            return;
        }
        guiGraphics.enableScissor(MARGIN, listTop, MARGIN + SIDEBAR_WIDTH, listBottom);
        for (int index = 0; index < friends.size(); index++) {
            int y = listTop + index * ROW_HEIGHT;
            if (y > listBottom) {
                break;
            }
            boolean hovered = mouseX >= MARGIN && mouseX < MARGIN + SIDEBAR_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            PinTheme.card(guiGraphics, MARGIN, y, SIDEBAR_WIDTH, ROW_HEIGHT - 2, hovered);
            guiGraphics.drawString(font, font.plainSubstrByWidth(friends.get(index), SIDEBAR_WIDTH - 8),
                    MARGIN + 5, y + 5, PinTheme.TEXT, false);
        }
        guiGraphics.disableScissor();
    }

    private void renderInbox(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int inboxWidth = width - MARGIN - inboxLeft;
        if (inbox.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.no_messages"),
                    inboxLeft + 2, listTop + 4, PinTheme.TEXT_MUTED, false);
            return;
        }
        int contentHeight = inbox.size() * MESSAGE_HEIGHT;
        inboxScroll = Math.clamp(inboxScroll, 0.0D, Math.max(0.0D, contentHeight - (listBottom - listTop)));
        guiGraphics.enableScissor(inboxLeft, listTop, inboxLeft + inboxWidth, listBottom);
        for (int index = 0; index < inbox.size(); index++) {
            PinFriends.Message message = inbox.get(index);
            int y = listTop + index * MESSAGE_HEIGHT - (int) inboxScroll;
            if (y + MESSAGE_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = mouseX >= inboxLeft && mouseX < inboxLeft + inboxWidth
                    && mouseY >= y && mouseY < y + MESSAGE_HEIGHT - 2 && mouseY >= listTop && mouseY < listBottom;
            PinTheme.card(guiGraphics, inboxLeft, y, inboxWidth - 6, MESSAGE_HEIGHT - 2, hovered);

            ThumbnailCache.Thumbnail thumbnail = ThumbnailCache.get(message.pin().thumbnailUrl());
            if (thumbnail != null) {
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, thumbnail.texture(),
                        inboxLeft + 2, y + 1, 0.0F, 0.0F, THUMB_SIZE, THUMB_SIZE,
                        thumbnail.width(), thumbnail.height(), thumbnail.width(), thumbnail.height(),
                        0xFFFFFFFF);
            }
            int textX = inboxLeft + THUMB_SIZE + 8;
            int textWidth = inboxWidth - THUMB_SIZE - 20;
            guiGraphics.drawString(font,
                    Component.translatable("screen.pinspo.message_from", message.from()),
                    textX, y + 6, PinTheme.ACCENT, false);
            guiGraphics.drawString(font, font.plainSubstrByWidth(message.note(), textWidth),
                    textX, y + 18, PinTheme.TEXT_MUTED, false);
        }
        guiGraphics.disableScissor();
        PinGrid.renderScrollbar(guiGraphics, inboxLeft + inboxWidth - 4, listTop, listBottom,
                inboxScroll, contentHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR_WIDTH
                && event.y() >= listTop && event.y() < listBottom) {
            int index = (int) ((event.y() - listTop) / ROW_HEIGHT);
            if (index >= 0 && index < friends.size()) {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    PinFriends.removeFriend(friends.get(index));
                    rebuild();
                } else {
                    sendToFriend(friends.get(index));
                }
            }
            return true;
        }
        if (event.x() >= inboxLeft && event.y() >= listTop && event.y() < listBottom) {
            int index = (int) ((event.y() - listTop + inboxScroll) / MESSAGE_HEIGHT);
            if (index >= 0 && index < inbox.size()) {
                PinterestApi.Pin pin = inbox.get(index).pin();
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    minecraft.setScreen(new FolderPickerScreen(this, pin));
                } else {
                    PinnedImage.pin(pin);
                    onClose();
                }
            }
            return true;
        }
        return false;
    }

    /** Copies a share code addressed to one friend, ready to paste to them. */
    private void sendToFriend(String friend) {
        if (PinHistory.entries().isEmpty()) {
            feedback = Component.translatable("screen.pinspo.nothing_to_send");
            return;
        }
        minecraft.keyboardHandler.setClipboard(
                PinFriends.compose(friend, PinHistory.entries().getFirst()));
        feedback = Component.translatable("screen.pinspo.send_to", friend);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= inboxLeft) {
            inboxScroll -= verticalAmount * MESSAGE_HEIGHT;
            return true;
        }
        return false;
    }
}
