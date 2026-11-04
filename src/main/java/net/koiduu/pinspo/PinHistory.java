package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The last few pins used as a reference, so a previous one can be brought back in two clicks. */
public final class PinHistory {

    public static final int MAX_ENTRIES = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-history.json");

    private static List<PinterestApi.Pin> entries = new ArrayList<>();
    private static boolean loaded;

    private PinHistory() {
    }

    public static List<PinterestApi.Pin> entries() {
        load();
        return List.copyOf(entries);
    }

    /** Records a pin as most recent, dropping the oldest beyond {@link #MAX_ENTRIES}. */
    public static void add(PinterestApi.Pin pin) {
        load();
        entries.removeIf(existing -> existing.imageUrl().equals(pin.imageUrl()));
        entries.addFirst(pin);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        save();
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
            List<PinterestApi.Pin> stored = GSON.fromJson(
                    reader, new TypeToken<ArrayList<PinterestApi.Pin>>() {}.getType());
            if (stored != null) {
                entries = new ArrayList<>(stored);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the pin history", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save the pin history", e);
        }
    }
}
