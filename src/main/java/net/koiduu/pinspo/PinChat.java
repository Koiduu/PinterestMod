package net.koiduu.pinspo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Friend messaging over Minecraft's own private messages, so a reference sent from PinSpo actually
 * reaches the other player without PinSpo needing a server: outgoing messages go out as {@code /msg},
 * and incoming whispers carrying a PinSpo share code are pulled into the Friends tab.
 */
public final class PinChat {

    /** Vanilla's own whisper line, plus the "From Name: text" shape used by Hypixel and friends. */
    private static final String VANILLA_INCOMING = "commands.message.display.incoming";
    private static final Pattern FROM_LINE = Pattern.compile(
            "^(?:From|from)\\s+(?:\\[[^]]*]\\s*)?([A-Za-z0-9_]{1,16})\\s*[:>]\\s*(.+)$");
    private static final Pattern WHISPER_LINE = Pattern.compile(
            "^([A-Za-z0-9_]{1,16})\\s+whispers(?:\\s+to\\s+you)?\\s*[:]\\s*(.+)$");
    /** Vanilla refuses longer chat messages, and a command adds its own prefix. */
    private static final int MAX_MESSAGE_LENGTH = 200;
    /** Friend requests travel as these marker messages, so both sides stay in step. */
    private static final String REQUEST = "PinSpo?friend";
    private static final String ACCEPT = "PinSpo!friend";
    private static final String DECLINE = "PinSpo-friend";

    private PinChat() {
    }

    /**
     * Sends a text message to a friend.
     *
     * @return false when there is no server connection or the name is not a valid player name
     */
    public static boolean sendText(String friend, String text) {
        String cleaned = PinSecurity.cleanText(text);
        if (cleaned.isBlank() || !send(friend, cleaned)) {
            return false;
        }
        PinFriends.recordSent(friend, cleaned, null);
        return true;
    }

    /**
     * Asks another player to be friends. The request is remembered locally either way, so it also works
     * as an invitation on servers that block private messages.
     *
     * @return true when the marker message actually went out
     */
    public static boolean sendRequest(String name) {
        boolean delivered = send(name, REQUEST);
        PinFriends.recordSentRequest(name);
        return delivered;
    }

    /** Accepts a pending request, telling the other side so they get the friendship too. */
    public static void acceptRequest(String name) {
        PinFriends.addFriend(name);
        send(name, ACCEPT);
    }

    /** Declines a pending request and lets the other side drop theirs. */
    public static void declineRequest(String name) {
        PinFriends.removeRequest(name);
        send(name, DECLINE);
    }

    /** Sends a reference to a friend as a share code their PinSpo turns back into a clickable pin. */
    public static boolean sendPin(String friend, PinterestApi.Pin pin) {
        String code = PinShare.encode(PinFriends.selfName(), java.util.List.of(pin));
        if (!PinShare.looksLikeCode(code) || !send(friend, code)) {
            return false;
        }
        PinFriends.recordSent(friend, code, pin);
        return true;
    }

    private static boolean send(String friend, String message) {
        // The name is checked against Minecraft's own name shape so it cannot extend the command.
        if (!PinSecurity.isPlayerName(friend) || message.length() > MAX_MESSAGE_LENGTH) {
            return false;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        connection.sendCommand("msg " + friend + " " + message);
        return true;
    }

    /** Files an incoming chat line into the Friends tab when it is a private message from a player. */
    public static void onChatMessage(Component message) {
        Incoming incoming = parse(message);
        if (incoming == null) {
            return;
        }
        String text = incoming.text();
        switch (text) {
            case REQUEST -> {
                PinFriends.recordIncomingRequest(incoming.sender());
                return;
            }
            case ACCEPT -> {
                // Only an answer to a request this player actually sent turns into a friendship.
                if (PinFriends.sentRequests().contains(incoming.sender())) {
                    PinFriends.addFriend(incoming.sender());
                }
                return;
            }
            case DECLINE -> {
                PinFriends.removeRequest(incoming.sender());
                return;
            }
            default -> {
            }
        }
        PinShare.Shared shared = PinShare.decode(text);
        if (shared == null) {
            // Plain text only counts as a PinSpo message once that player is a friend.
            if (PinFriends.isFriend(incoming.sender())) {
                PinFriends.recordReceived(incoming.sender(), text, null);
            }
            return;
        }
        if (!PinFriends.isFriend(incoming.sender())) {
            // A reference from a stranger arrives as a friend request instead of straight into the inbox.
            PinFriends.recordIncomingRequest(incoming.sender());
            return;
        }
        for (PinterestApi.Pin pin : shared.pins()) {
            PinFriends.recordReceived(incoming.sender(), text, pin);
        }
    }

    private record Incoming(String sender, String text) {
    }

    @Nullable
    private static Incoming parse(Component message) {
        if (message.getContents() instanceof TranslatableContents contents
                && VANILLA_INCOMING.equals(contents.getKey())
                && contents.getArgs().length >= 2) {
            String sender = plain(contents.getArgs()[0]);
            String text = plain(contents.getArgs()[1]);
            return PinSecurity.isPlayerName(sender) ? new Incoming(sender, text) : null;
        }
        String line = PinSecurity.cleanText(message.getString());
        Matcher from = FROM_LINE.matcher(line);
        if (from.matches()) {
            return new Incoming(from.group(1), from.group(2));
        }
        Matcher whisper = WHISPER_LINE.matcher(line);
        return whisper.matches() ? new Incoming(whisper.group(1), whisper.group(2)) : null;
    }

    private static String plain(Object argument) {
        return argument instanceof Component component
                ? PinSecurity.cleanText(component.getString())
                : PinSecurity.cleanText(String.valueOf(argument));
    }
}
