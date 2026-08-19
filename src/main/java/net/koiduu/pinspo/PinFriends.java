package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The friend list and the chat history kept with each friend, stored in {@code pinspo-friends.json}. */
public final class PinFriends {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-friends.json");
    /** Messages kept per conversation; older ones are dropped. */
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_FRIENDS = 100;

    /**
     * One line of a conversation. Exactly one of {@code text} and {@code pin} is meaningful: a pin
     * message is drawn as a clickable reference, anything else as chat text.
     */
    public record Message(String friend, boolean outgoing, String text, @Nullable PinterestApi.Pin pin,
                          long sentAt) {
    }

    private static Stored stored = new Stored();
    private static boolean loaded;

    private PinFriends() {
    }

    public static List<String> friends() {
        return List.copyOf(all().friends);
    }

    /** The conversation with {@code friend}, oldest message first. */
    public static List<Message> conversation(String friend) {
        return List.copyOf(all().chats.getOrDefault(friend, List.of()));
    }

    /** Total messages that have not been looked at yet, per friend. */
    public static int unread(String friend) {
        return all().unread.getOrDefault(friend, 0);
    }

    public static void markRead(String friend) {
        if (all().unread.remove(friend) != null) {
            save();
        }
    }

    /** Requests the player sent that have not been answered yet. */
    public static List<String> sentRequests() {
        return List.copyOf(all().sent);
    }

    /** Requests from other players waiting to be accepted or declined. */
    public static List<String> pendingRequests() {
        return List.copyOf(all().pending);
    }

    public static boolean isFriend(String name) {
        return all().friends.contains(name);
    }

    /**
     * Adds a friend by Minecraft name, without going through a request. Used when a request is accepted
     * and when a code is imported by hand.
     *
     * @return true when the name was valid and is now on the list
     */
    public static boolean addFriend(String rawName) {
        String name = rawName.trim();
        if (!PinSecurity.isPlayerName(name) || all().friends.size() >= MAX_FRIENDS) {
            return false;
        }
        boolean changed = all().pending.remove(name);
        changed |= all().sent.remove(name);
        if (!all().friends.contains(name)) {
            all().friends.add(name);
            changed = true;
        }
        if (changed) {
            save();
        }
        return true;
    }

    /**
     * Notes that the player asked {@code name} to be friends, so the answer can be matched up later.
     *
     * @return true when the name was valid and the request is now pending
     */
    public static boolean recordSentRequest(String rawName) {
        String name = rawName.trim();
        if (!PinSecurity.isPlayerName(name) || all().sent.size() >= MAX_FRIENDS
                || all().friends.contains(name)) {
            return false;
        }
        if (!all().sent.contains(name)) {
            all().sent.add(name);
            save();
        }
        return true;
    }

    /**
     * Files a request received from another player. Ignored when they are already a friend.
     *
     * @return true when this is news, so it is worth telling the player about
     */
    public static boolean recordIncomingRequest(String rawName) {
        String name = rawName.trim();
        if (!PinSecurity.isPlayerName(name) || all().friends.contains(name)
                || all().pending.size() >= MAX_FRIENDS) {
            return false;
        }
        // A request that crosses one the player already sent simply becomes a friendship.
        if (all().sent.contains(name)) {
            return addFriend(name);
        }
        if (all().pending.contains(name)) {
            return false;
        }
        all().pending.add(name);
        save();
        return true;
    }

    /** Drops a request in either direction, e.g. when it is declined or cancelled. */
    public static void removeRequest(String name) {
        boolean changed = all().pending.remove(name);
        changed |= all().sent.remove(name);
        if (changed) {
            save();
        }
    }

    public static void removeFriend(String friend) {
        boolean changed = all().friends.remove(friend);
        changed |= all().pending.remove(friend);
        changed |= all().sent.remove(friend);
        changed |= all().chats.remove(friend) != null;
        changed |= all().unread.remove(friend) != null;
        if (changed) {
            save();
        }
    }

    /** Records a message the player just sent to {@code friend}. */
    public static void recordSent(String friend, String text, @Nullable PinterestApi.Pin pin) {
        append(new Message(friend, true, PinSecurity.cleanText(text), pin, System.currentTimeMillis()));
    }

    /** Records a message received from {@code friend}, and counts it as unread. */
    public static void recordReceived(String friend, String text, @Nullable PinterestApi.Pin pin) {
        append(new Message(friend, false, PinSecurity.cleanText(text), pin, System.currentTimeMillis()));
        all().unread.merge(friend, 1, Integer::sum);
        save();
    }

    public static void clearConversation(String friend) {
        if (all().chats.remove(friend) != null) {
            all().unread.remove(friend);
            save();
        }
    }

    private static void append(Message message) {
        String friend = message.friend();
        if (!PinSecurity.isPlayerName(friend)) {
            return;
        }
        List<Message> chat = all().chats.computeIfAbsent(friend, key -> new ArrayList<>());
        chat.add(message);
        while (chat.size() > MAX_MESSAGES) {
            chat.removeFirst();
        }
        save();
    }

    /** The player's own Minecraft name, used as the sender label on outgoing references. */
    public static String selfName() {
        Player player = Minecraft.getInstance().player;
        return player == null ? "PinSpo" : player.getGameProfile().name();
    }

    private static Stored all() {
        if (!loaded) {
            loaded = true;
            if (Files.isRegularFile(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    Stored read = GSON.fromJson(reader, Stored.class);
                    if (read != null) {
                        stored = read;
                    }
                } catch (Exception e) {
                    PinSpoClient.LOGGER.warn("Could not read the PinSpo friend list", e);
                }
            }
            stored.normalise();
        }
        return stored;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save the PinSpo friend list", e);
        }
    }

    private static final class Stored {
        List<String> friends = new ArrayList<>();
        /** Requests waiting for this player to answer. */
        List<String> pending = new ArrayList<>();
        /** Requests this player sent and that have not been answered. */
        List<String> sent = new ArrayList<>();
        Map<String, List<Message>> chats = new LinkedHashMap<>();
        Map<String, Integer> unread = new LinkedHashMap<>();

        /** Drops anything a hand-edited or older file might contain that the screens cannot handle. */
        void normalise() {
            friends = names(friends);
            pending = names(pending);
            sent = names(sent);
            pending.removeAll(friends);
            sent.removeAll(friends);
            chats = chats == null ? new LinkedHashMap<>() : new LinkedHashMap<>(chats);
            unread = unread == null ? new LinkedHashMap<>() : new LinkedHashMap<>(unread);
            chats.entrySet().removeIf(entry ->
                    entry.getKey() == null || !PinSecurity.isPlayerName(entry.getKey()) || entry.getValue() == null);
            chats.replaceAll((friend, messages) -> {
                List<Message> cleaned = new ArrayList<>();
                for (Message message : messages) {
                    if (message == null || message.pin() != null && !PinSecurity.isAllowedPin(message.pin())) {
                        continue;
                    }
                    cleaned.add(new Message(friend, message.outgoing(),
                            PinSecurity.cleanText(message.text()), message.pin(), message.sentAt()));
                }
                return cleaned;
            });
        }

        private static List<String> names(@Nullable List<String> raw) {
            List<String> cleaned = raw == null ? new ArrayList<>() : new ArrayList<>(raw);
            cleaned.removeIf(name -> name == null || !PinSecurity.isPlayerName(name));
            return cleaned;
        }
    }
}
