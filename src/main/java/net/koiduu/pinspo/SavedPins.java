package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The player's own reference folders, stored locally so saved pins work offline and load instantly. */
public final class SavedPins {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-saved.json");
    public static final String DEFAULT_FOLDER = "References";
    private static final int MAX_FOLDERS = 200;
    private static final int MAX_PINS_PER_FOLDER = 500;

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

    /**
     * Creates a folder, returning the name it was stored under (cleaned of anything unsafe) or an empty
     * string when the name was unusable.
     */
    public static String createFolder(String folder) {
        String name = PinSecurity.cleanName(folder);
        if (name.isEmpty()) {
            return "";
        }
        if (all().putIfAbsent(name, new ArrayList<>()) == null) {
            save();
        }
        return name;
    }

    public static void deleteFolder(String folder) {
        if (all().remove(folder) != null) {
            save();
        }
    }

    /** Adds a pin to a folder, creating the folder if needed and ignoring duplicates. */
    public static void add(String folder, PinterestApi.Pin pin) {
        String name = PinSecurity.cleanName(folder);
        if (name.isEmpty() || !PinSecurity.isAllowedPin(pin) || all().size() > MAX_FOLDERS) {
            return;
        }
        List<PinterestApi.Pin> pins = all().computeIfAbsent(name, key -> new ArrayList<>());
        if (pins.size() < MAX_PINS_PER_FOLDER
                && pins.stream().noneMatch(existing -> existing.imageUrl().equals(pin.imageUrl()))) {
            pins.add(pin);
            save();
        }
    }

    public static void remove(String folder, PinterestApi.Pin pin) {
        List<PinterestApi.Pin> pins = all().get(PinSecurity.cleanName(folder));
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
            return stored == null ? new LinkedHashMap<>() : normalise(stored);
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read saved pins", e);
            return new LinkedHashMap<>();
        }
    }

    /** Keeps every folder the file names, dropping only entries the screens could not draw. */
    private static Map<String, List<PinterestApi.Pin>> normalise(Map<String, List<PinterestApi.Pin>> stored) {
        Map<String, List<PinterestApi.Pin>> cleaned = new LinkedHashMap<>();
        stored.forEach((folder, pins) -> {
            String name = PinSecurity.cleanName(folder);
            if (name.isEmpty()) {
                return;
            }
            List<PinterestApi.Pin> kept = cleaned.computeIfAbsent(name, key -> new ArrayList<>());
            if (pins == null) {
                return;
            }
            for (PinterestApi.Pin pin : pins) {
                if (PinSecurity.isAllowedPin(pin)
                        && kept.stream().noneMatch(existing -> existing.imageUrl().equals(pin.imageUrl()))) {
                    kept.add(pin);
                }
            }
        });
        return cleaned;
    }

    /**
     * Writes to a temporary file and moves it over the real one, so a crash or a full disk cannot leave a
     * half-written file that loses every folder.
     */
    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(folders, writer);
            }
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save pins", e);
        }
    }
}
