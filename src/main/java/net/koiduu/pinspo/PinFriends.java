package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Friends and the reference inbox. PinSpo is client-side and has no server of its own, so a reference is
 * sent as a share code (chat, Discord, anywhere) and arrives here when the friend imports it.
 */
public final class PinFriends {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-friends.json");
    private static final int MAX_MESSAGES = 50;

    /** One received reference: who it came from, what it was called, and the pin itself. */
    public record Message(String from, String note, PinterestApi.Pin pin, long receivedAt) {
    }

    private static Stored stored = new Stored();
    private static boolean loaded;

    private PinFriends() {
    }

    public static List<String> friends() {
        load();
        return List.copyOf(stored.friends);
    }

    public static List<Message> inbox() {
        load();
        return List.copyOf(stored.inbox);
    }

    public static void addFriend(String name) {
        load();
        String trimmed = name.trim();
        if (trimmed.isEmpty() || stored.friends.contains(trimmed)) {
            return;
        }
        stored.friends.add(trimmed);
        save();
    }

    public static void removeFriend(String name) {
        load();
        if (stored.friends.remove(name)) {
            save();
        }
    }

    /** Builds the share code a friend pastes into their own inbox. */
    public static String compose(String recipient, PinterestApi.Pin pin) {
        addFriend(recipient);
        return PinShare.encode(senderName(), List.of(pin));
    }

    /**
     * Reads a share code as an incoming message.
     *
     * @return the number of references received, or 0 when the code was not valid
     */
    public static int receive(String code) {
        PinShare.Shared shared = PinShare.decode(code);
        if (shared == null) {
            return 0;
        }
        load();
        String from = shared.name() == null || shared.name().isBlank() ? "Someone" : shared.name();
        long now = System.currentTimeMillis();
        for (PinterestApi.Pin pin : shared.pins()) {
            stored.inbox.addFirst(new Message(from, pin.title(), pin, now));
        }
        while (stored.inbox.size() > MAX_MESSAGES) {
            stored.inbox.removeLast();
        }
        addFriend(from);
        save();
        return shared.pins().size();
    }

    public static void clearInbox() {
        load();
        stored.inbox.clear();
        save();
    }

    /** The name attached to outgoing references: the player's own Minecraft name. */
    public static String senderName() {
        Player player = Minecraft.getInstance().player;
        return player == null ? "A builder" : player.getGameProfile().name();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Stored read = GSON.fromJson(reader, Stored.class);
            if (read != null) {
                stored = read;
                stored.normalise();
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the PinSpo friend list", e);
        }
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
        List<Message> inbox = new ArrayList<>();

        void normalise() {
            friends = friends == null ? new ArrayList<>() : new ArrayList<>(friends);
            inbox = inbox == null ? new ArrayList<>() : new ArrayList<>(inbox);
        }
    }
}
