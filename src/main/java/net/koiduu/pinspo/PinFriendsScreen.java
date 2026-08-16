package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

/**
 * Friends tab: friend list on the left, the conversation with the selected friend on the right. Messages
 * travel as private messages, so a reference sent here shows up in the other player's PinSpo.
 */
public class PinFriendsScreen extends PinTabScreen {

    private static final int SIDEBAR_WIDTH = 110;
    private static final int FRIEND_ROW_HEIGHT = 20;
    private static final int REQUEST_ROW_HEIGHT = 20;
    /** Requests are rare, so only a few are shown at a time and the rest are counted. */
    private static final int MAX_SHOWN_REQUESTS = 3;
    private static final int BUBBLE_HEIGHT = 16;
    private static final int PIN_BUBBLE_HEIGHT = 40;
    private static final int PIN_THUMB = 36;

    private List<String> friends = List.of();
    private List<String> requests = List.of();
    private List<PinFriends.Message> conversation = List.of();
    @Nullable
    private String selected;

    @Nullable
    private EditBox addBox;
    @Nullable
    private EditBox messageBox;
    @Nullable
    private Component feedback;
    private double chatScroll;
    private int listTop;
    private int friendsTop;
    private int listBottom;
    private int chatLeft;
    private int chatBottom;

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
        requests = PinFriends.pendingRequests();
        if (selected == null || !friends.contains(selected)) {
            selected = friends.isEmpty() ? null : friends.getFirst();
        }
        if (selected != null) {
            PinFriends.markRead(selected);
        }
        conversation = selected == null ? List.of() : PinFriends.conversation(selected);

        listTop = CONTENT_TOP + 24;
        friendsTop = listTop + requestsHeight();
        listBottom = height - FOOTER_HEIGHT - 8;
        chatLeft = MARGIN + SIDEBAR_WIDTH + 12;
        chatBottom = listBottom - 24;

        addTabs();

        addBox = new EditBox(font, MARGIN, CONTENT_TOP, SIDEBAR_WIDTH - 22, 18,
                Component.translatable("screen.pinspo.friend_name"));
        addBox.setHint(Component.translatable("screen.pinspo.friend_hint"));
        addBox.setMaxLength(16);
        addRenderableWidget(addBox);
        addRenderableWidget(PinButton.primary(MARGIN + SIDEBAR_WIDTH - 20, CONTENT_TOP, 20, 18,
                Component.literal("+"), this::addFriend));
        addRequestButtons();

        PinButton sendPin = PinButton.primary(chatLeft, CONTENT_TOP, 120, 18,
                Component.translatable("screen.pinspo.send_pin"), this::sendCurrentPin);
        sendPin.active = selected != null && !PinHistory.entries().isEmpty();
        addRenderableWidget(sendPin);
        PinButton copyCode = PinButton.of(chatLeft + 124, CONTENT_TOP, 96, 18,
                Component.translatable("screen.pinspo.copy_code"), this::copyCurrentPin);
        copyCode.active = !PinHistory.entries().isEmpty();
        addRenderableWidget(copyCode);
        PinButton paste = PinButton.of(chatLeft + 224, CONTENT_TOP, 96, 18,
                Component.translatable("screen.pinspo.paste_code"), this::pasteCode);
        paste.active = selected != null;
        addRenderableWidget(paste);

        messageBox = new EditBox(font, chatLeft, chatBottom + 4, width - MARGIN - chatLeft - 54, 18,
                Component.translatable("screen.pinspo.message"));
        messageBox.setHint(Component.translatable("screen.pinspo.message_hint"));
        messageBox.setMaxLength(180);
        messageBox.setEditable(selected != null);
        addRenderableWidget(messageBox);
        PinButton send = PinButton.primary(width - MARGIN - 50, chatBottom + 4, 50, 18,
                Component.translatable("screen.pinspo.send"), this::sendMessage);
        send.active = selected != null;
        addRenderableWidget(send);

        chatScroll = Math.max(0, contentHeight() - (chatBottom - listTop));
    }

    /** Accept and decline buttons for each request waiting to be answered. */
    private void addRequestButtons() {
        int shown = Math.min(requests.size(), MAX_SHOWN_REQUESTS);
        for (int index = 0; index < shown; index++) {
            String name = requests.get(index);
            int y = listTop + 10 + index * REQUEST_ROW_HEIGHT;
            addRenderableWidget(PinButton.primary(MARGIN + SIDEBAR_WIDTH - 38, y, 18, 18,
                    Component.literal("\u2713"), () -> {
                        PinChat.acceptRequest(name);
                        selected = name;
                        feedback = Component.translatable("screen.pinspo.request_accepted", name);
                        rebuild();
                    }));
            addRenderableWidget(PinButton.of(MARGIN + SIDEBAR_WIDTH - 18, y, 18, 18,
                    Component.literal("\u2715"), () -> {
                        PinChat.declineRequest(name);
                        feedback = null;
                        rebuild();
                    }));
        }
    }

    private int requestsHeight() {
        if (requests.isEmpty()) {
            return 0;
        }
        return 10 + Math.min(requests.size(), MAX_SHOWN_REQUESTS) * REQUEST_ROW_HEIGHT
                + (requests.size() > MAX_SHOWN_REQUESTS ? 10 : 0) + 6;
    }

    /** Sends a friend request instead of adding the player straight away. */
    private void addFriend() {
        if (addBox == null || addBox.getValue().isBlank()) {
            return;
        }
        String name = addBox.getValue().trim();
        if (!PinSecurity.isPlayerName(name)) {
            feedback = Component.translatable("screen.pinspo.bad_name");
            return;
        }
        if (PinFriends.isFriend(name)) {
            selected = name;
            addBox.setValue("");
            feedback = null;
            rebuild();
            return;
        }
        if (PinFriends.pendingRequests().contains(name)) {
            PinChat.acceptRequest(name);
            selected = name;
            addBox.setValue("");
            feedback = Component.translatable("screen.pinspo.request_accepted", name);
            rebuild();
            return;
        }
        boolean delivered = PinChat.sendRequest(name);
        addBox.setValue("");
        feedback = Component.translatable(delivered
                ? "screen.pinspo.request_sent"
                : "screen.pinspo.request_offline", name);
        rebuild();
    }

    private void sendMessage() {
        if (selected == null || messageBox == null || messageBox.getValue().isBlank()) {
            return;
        }
        if (!PinChat.sendText(selected, messageBox.getValue())) {
            feedback = Component.translatable("screen.pinspo.send_failed");
            return;
        }
        messageBox.setValue("");
        feedback = null;
        rebuild();
    }

    private void sendCurrentPin() {
        PinterestApi.Pin pin = currentPin();
        if (selected == null || pin == null) {
            feedback = Component.translatable("screen.pinspo.nothing_to_send");
            return;
        }
        if (!PinShare.isShareable(pin)) {
            feedback = Component.translatable("screen.pinspo.not_shareable");
            return;
        }
        if (!PinChat.sendPin(selected, pin)) {
            feedback = Component.translatable("screen.pinspo.send_failed");
            return;
        }
        feedback = null;
        rebuild();
    }

    /** Fallback for players on servers that block private messages: hand over the code by hand. */
    private void copyCurrentPin() {
        PinterestApi.Pin pin = currentPin();
        if (pin == null) {
            feedback = Component.translatable("screen.pinspo.nothing_to_send");
            return;
        }
        if (!PinShare.isShareable(pin)) {
            feedback = Component.translatable("screen.pinspo.not_shareable");
            return;
        }
        minecraft.keyboardHandler.setClipboard(PinShare.encode(PinFriends.selfName(), List.of(pin)));
        feedback = Component.translatable("screen.pinspo.send_copied");
    }

    private void pasteCode() {
        if (selected == null) {
            return;
        }
        PinShare.Shared shared = PinShare.decode(minecraft.keyboardHandler.getClipboard());
        if (shared == null) {
            feedback = Component.translatable("screen.pinspo.import_failed");
            return;
        }
        for (PinterestApi.Pin pin : shared.pins()) {
            PinFriends.recordReceived(selected, "", pin);
        }
        PinFriends.markRead(selected);
        feedback = Component.translatable("screen.pinspo.received", shared.pins().size());
        rebuild();
    }

    @Nullable
    private PinterestApi.Pin currentPin() {
        return PinHistory.entries().isEmpty() ? null : PinHistory.entries().getFirst();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        PinTheme.panel(guiGraphics, MARGIN - 4, listTop - 4, SIDEBAR_WIDTH + 8, listBottom - listTop + 8);
        PinTheme.panel(guiGraphics, chatLeft - 4, listTop - 4, width - MARGIN - chatLeft + 8,
                listBottom - listTop + 8);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderFriends(guiGraphics, mouseX, mouseY);
        renderConversation(guiGraphics, mouseX, mouseY);

        Component hint = feedback != null
                ? feedback
                : Component.translatable(sentHintKey());
        guiGraphics.drawString(font, font.plainSubstrByWidth(hint.getString(), width - MARGIN * 2 - 90),
                MARGIN, height - FOOTER_HEIGHT + 12,
                feedback != null ? PinTheme.ACCENT : PinTheme.TEXT_MUTED, false);
    }

    private void renderFriends(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderRequests(guiGraphics);
        if (friends.isEmpty()) {
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(
                            Component.translatable("screen.pinspo.no_friends").getString(), SIDEBAR_WIDTH),
                    MARGIN + 2, friendsTop + 4, PinTheme.TEXT_MUTED, false);
            return;
        }
        guiGraphics.enableScissor(MARGIN, friendsTop, MARGIN + SIDEBAR_WIDTH, listBottom);
        for (int index = 0; index < friends.size(); index++) {
            String friend = friends.get(index);
            int y = friendsTop + index * FRIEND_ROW_HEIGHT;
            if (y > listBottom) {
                break;
            }
            boolean hovered = mouseX >= MARGIN && mouseX < MARGIN + SIDEBAR_WIDTH
                    && mouseY >= y && mouseY < y + FRIEND_ROW_HEIGHT - 2;
            boolean isSelected = Objects.equals(friend, selected);
            PinTheme.card(guiGraphics, MARGIN, y, SIDEBAR_WIDTH, FRIEND_ROW_HEIGHT - 2, hovered || isSelected);
            if (isSelected) {
                guiGraphics.fill(MARGIN, y, MARGIN + 2, y + FRIEND_ROW_HEIGHT - 2, PinTheme.ACCENT);
            }
            int unread = PinFriends.unread(friend);
            int nameWidth = SIDEBAR_WIDTH - 10 - (unread > 0 ? 14 : 0);
            guiGraphics.drawString(font, font.plainSubstrByWidth(friend, nameWidth),
                    MARGIN + 6, y + 5, isSelected ? PinTheme.TEXT : PinTheme.TEXT_MUTED, false);
            if (unread > 0) {
                String badge = unread > 9 ? "9+" : String.valueOf(unread);
                guiGraphics.fill(MARGIN + SIDEBAR_WIDTH - 16, y + 3,
                        MARGIN + SIDEBAR_WIDTH - 4, y + 14, PinTheme.ACCENT);
                guiGraphics.drawString(font, badge, MARGIN + SIDEBAR_WIDTH - 13, y + 5, PinTheme.TEXT, false);
            }
        }
        guiGraphics.disableScissor();
    }

    /** The requests waiting to be answered, above the friend list, each with its own accept/decline. */
    private void renderRequests(GuiGraphics guiGraphics) {
        if (requests.isEmpty()) {
            return;
        }
        guiGraphics.drawString(font, Component.translatable("screen.pinspo.requests"),
                MARGIN + 2, listTop, PinTheme.ACCENT, false);
        int shown = Math.min(requests.size(), MAX_SHOWN_REQUESTS);
        for (int index = 0; index < shown; index++) {
            int y = listTop + 10 + index * REQUEST_ROW_HEIGHT;
            PinTheme.card(guiGraphics, MARGIN, y, SIDEBAR_WIDTH, REQUEST_ROW_HEIGHT - 2, false);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(requests.get(index), SIDEBAR_WIDTH - 44),
                    MARGIN + 4, y + 5, PinTheme.TEXT, false);
        }
        if (requests.size() > shown) {
            guiGraphics.drawString(font,
                    Component.translatable("screen.pinspo.more_requests", requests.size() - shown),
                    MARGIN + 2, listTop + 10 + shown * REQUEST_ROW_HEIGHT, PinTheme.TEXT_MUTED, false);
        }
    }

    private void renderConversation(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int chatWidth = width - MARGIN - chatLeft;
        if (selected == null) {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.no_friends_selected"),
                    chatLeft + 2, listTop + 4, PinTheme.TEXT_MUTED, false);
            return;
        }
        if (conversation.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.no_messages"),
                    chatLeft + 2, listTop + 4, PinTheme.TEXT_MUTED, false);
            return;
        }
        int content = contentHeight();
        chatScroll = Math.clamp(chatScroll, 0.0D, Math.max(0.0D, content - (chatBottom - listTop)));
        guiGraphics.enableScissor(chatLeft, listTop, chatLeft + chatWidth, chatBottom);
        int y = listTop - (int) chatScroll;
        for (PinFriends.Message message : conversation) {
            int bubbleHeight = message.pin() == null ? BUBBLE_HEIGHT : PIN_BUBBLE_HEIGHT;
            if (y + bubbleHeight >= listTop && y <= chatBottom) {
                renderBubble(guiGraphics, message, y, chatWidth, mouseX, mouseY);
            }
            y += bubbleHeight + 2;
        }
        guiGraphics.disableScissor();
        PinGrid.renderScrollbar(guiGraphics, chatLeft + chatWidth - 4, listTop, chatBottom, chatScroll, content);
    }

    private void renderBubble(GuiGraphics guiGraphics, PinFriends.Message message, int y, int chatWidth,
                              int mouseX, int mouseY) {
        boolean outgoing = message.outgoing();
        int bubbleWidth = message.pin() == null
                ? Math.min(chatWidth - 20, font.width(bubbleText(message)) + 14)
                : Math.min(chatWidth - 20, PIN_THUMB + 120);
        int x = outgoing ? chatLeft + chatWidth - 10 - bubbleWidth : chatLeft + 4;
        int bubbleHeight = message.pin() == null ? BUBBLE_HEIGHT : PIN_BUBBLE_HEIGHT;
        boolean hovered = message.pin() != null && mouseX >= x && mouseX < x + bubbleWidth
                && mouseY >= y && mouseY < y + bubbleHeight && mouseY >= listTop && mouseY < chatBottom;

        PinTheme.roundedRect(guiGraphics, x, y, bubbleWidth, bubbleHeight,
                outgoing ? PinTheme.ACCENT_DIM : PinTheme.CARD);
        PinTheme.roundedOutline(guiGraphics, x, y, bubbleWidth, bubbleHeight,
                hovered ? PinTheme.ACCENT : PinTheme.BORDER);

        if (message.pin() == null) {
            guiGraphics.drawString(font, font.plainSubstrByWidth(bubbleText(message), bubbleWidth - 10),
                    x + 5, y + 4, PinTheme.TEXT, false);
            return;
        }
        ThumbnailCache.Thumbnail thumbnail = ThumbnailCache.get(message.pin().thumbnailUrl());
        if (thumbnail != null) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, thumbnail.texture(), x + 2, y + 2, 0.0F, 0.0F,
                    PIN_THUMB, PIN_THUMB, thumbnail.width(), thumbnail.height(),
                    thumbnail.width(), thumbnail.height(), 0xFFFFFFFF);
        }
        guiGraphics.drawString(font, Component.translatable("screen.pinspo.reference"),
                x + PIN_THUMB + 8, y + 8, PinTheme.TEXT, false);
        guiGraphics.drawString(font, Component.translatable("screen.pinspo.click_to_pin"),
                x + PIN_THUMB + 8, y + 22, hovered ? PinTheme.ACCENT : PinTheme.TEXT_MUTED, false);
    }

    /** The footer hint depends on whether there is anything to explain yet. */
    private String sentHintKey() {
        if (!PinFriends.sentRequests().isEmpty() && friends.isEmpty()) {
            return "screen.pinspo.request_waiting";
        }
        return selected == null ? "screen.pinspo.friends_empty_hint" : "screen.pinspo.friends_hint";
    }

    private String bubbleText(PinFriends.Message message) {
        return message.text().isBlank() ? " " : message.text();
    }

    private int contentHeight() {
        int total = 0;
        for (PinFriends.Message message : conversation) {
            total += (message.pin() == null ? BUBBLE_HEIGHT : PIN_BUBBLE_HEIGHT) + 2;
        }
        return total;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR_WIDTH
                && event.y() >= friendsTop && event.y() < listBottom) {
            int index = (int) ((event.y() - friendsTop) / FRIEND_ROW_HEIGHT);
            if (index >= 0 && index < friends.size()) {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    PinFriends.removeFriend(friends.get(index));
                    selected = null;
                } else {
                    selected = friends.get(index);
                }
                feedback = null;
                rebuild();
            }
            return true;
        }
        if (event.x() >= chatLeft && event.y() >= listTop && event.y() < chatBottom) {
            PinFriends.Message message = messageAt(event.y());
            if (message != null && message.pin() != null) {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    minecraft.setScreen(new PinActionScreen(this, message.pin()));
                } else {
                    PinnedImage.pin(message.pin());
                    onClose();
                }
                return true;
            }
        }
        return false;
    }

    @Nullable
    private PinFriends.Message messageAt(double mouseY) {
        int y = listTop - (int) chatScroll;
        for (PinFriends.Message message : conversation) {
            int bubbleHeight = message.pin() == null ? BUBBLE_HEIGHT : PIN_BUBBLE_HEIGHT;
            if (mouseY >= y && mouseY < y + bubbleHeight) {
                return message;
            }
            y += bubbleHeight + 2;
        }
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && messageBox != null && messageBox.isFocused()) {
            sendMessage();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= chatLeft) {
            chatScroll -= verticalAmount * BUBBLE_HEIGHT * 2;
            return true;
        }
        return false;
    }
}
