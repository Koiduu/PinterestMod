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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The player's own reference folders, stored locally so saved pins work offline and load instantly. */
public final class SavedPins {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-saved.json");
    public static final String DEFAULT_FOLDER = "References";

    private static Map<String, List<PinterestApi.Pin>> folders = new LinkedHashMap<>();
    private static boolean loaded;

    private SavedPins() {
    }

    public static List<String> folderNames() {
        return new ArrayList<>(all().keySet());
    }

    public static List<PinterestApi.Pin> pins(String folder) {
        return all().getOrDefault(folder, List.of());
    }

    public static void createFolder(String folder) {
        if (!folder.isBlank()) {
            all().computeIfAbsent(folder.trim(), key -> new ArrayList<>());
            save();
        }
    }

    public static void deleteFolder(String folder) {
        if (all().remove(folder) != null) {
            save();
        }
    }

    /** Adds a pin to a folder, creating the folder if needed and ignoring duplicates. */
    public static void add(String folder, PinterestApi.Pin pin) {
        List<PinterestApi.Pin> pins = all().computeIfAbsent(folder, key -> new ArrayList<>());
        if (pins.stream().noneMatch(existing -> existing.imageUrl().equals(pin.imageUrl()))) {
            pins.add(pin);
            save();
        }
    }

    public static void remove(String folder, PinterestApi.Pin pin) {
        List<PinterestApi.Pin> pins = all().get(folder);
        if (pins != null && pins.removeIf(existing -> existing.imageUrl().equals(pin.imageUrl()))) {
            save();
        }
    }

    private static Map<String, List<PinterestApi.Pin>> all() {
        if (!loaded) {
            loaded = true;
            folders = read();
            if (folders.isEmpty()) {
                folders.put(DEFAULT_FOLDER, new ArrayList<>());
            }
        }
        return folders;
    }

    private static Map<String, List<PinterestApi.Pin>> read() {
        if (!Files.isRegularFile(PATH)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            Map<String, List<PinterestApi.Pin>> stored = GSON.fromJson(
                    reader, new TypeToken<LinkedHashMap<String, List<PinterestApi.Pin>>>() {}.getType());
            return stored == null ? new LinkedHashMap<>() : stored;
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read saved pins", e);
            return new LinkedHashMap<>();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(folders, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save pins", e);
        }
    }
}
