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

    /**
     * Adds a friend by Minecraft name.
     *
     * @return true when the name was valid and is now on the list
     */
    public static boolean addFriend(String rawName) {
        String name = rawName.trim();
        if (!PinSecurity.isPlayerName(name) || all().friends.size() >= MAX_FRIENDS) {
            return false;
        }
        if (!all().friends.contains(name)) {
            all().friends.add(name);
            save();
        }
        return true;
    }

    public static void removeFriend(String friend) {
        boolean changed = all().friends.remove(friend);
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
        addFriend(friend);
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
        Map<String, List<Message>> chats = new LinkedHashMap<>();
        Map<String, Integer> unread = new LinkedHashMap<>();

        /** Drops anything a hand-edited or older file might contain that the screens cannot handle. */
        void normalise() {
            friends = friends == null ? new ArrayList<>() : new ArrayList<>(friends);
            friends.removeIf(name -> name == null || !PinSecurity.isPlayerName(name));
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
    }
}
