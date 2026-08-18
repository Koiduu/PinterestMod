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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player keeps picking. Every used reference bumps the words behind it, and the home feed and the
 * search suggestions are built from the heaviest ones, so recommendations drift towards their own style.
 */
public final class PinTaste {

    /** Seeds the feed until the player has clicked enough for their own words to take over. */
    private static final List<String> DEFAULT_TOPICS = List.of(
            "minecraft build ideas", "medieval castle", "cottagecore house", "japanese garden",
            "modern villa", "fantasy treehouse", "desert temple", "gothic cathedral",
            "viking village", "stone bridge");

    /** Words that say nothing about a build, so they never become an interest. */
    private static final Set<String> IGNORED = Set.of(
            "the", "and", "for", "with", "from", "this", "that", "your", "you", "are", "was", "our",
            "ideas", "idea", "image", "images", "photo", "photos", "pin", "pins", "pinterest",
            "best", "top", "new", "how", "diy", "com", "www", "http", "https", "png", "jpg", "jpeg");

    private static final int MAX_INTERESTS = 40;
    private static final int QUERY_WEIGHT = 3;
    private static final int TITLE_WEIGHT = 1;
    private static final int MAX_WORD_LENGTH = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-taste.json");

    private static Map<String, Integer> weights = new LinkedHashMap<>();
    private static boolean loaded;

    private PinTaste() {
    }

    /** Records that a reference was used, from the words in its own title. */
    public static void record(PinterestApi.Pin pin) {
        record(pin.title(), TITLE_WEIGHT);
    }

    /** Records the search a used reference came from; it counts for more, because the player typed it. */
    public static void recordQuery(String query) {
        record(query, QUERY_WEIGHT);
    }

    private static void record(String text, int weight) {
        load();
        List<String> words = words(text);
        if (words.isEmpty()) {
            return;
        }
        for (String word : words) {
            bump(word, weight);
        }
        prune();
        save();
    }

    /** The player's heaviest interests, most used first. */
    public static List<String> interests(int limit) {
        load();
        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Chips for the empty search screen: the player's own words first, then seeds to fill the row. */
    public static List<String> suggestions(int limit) {
        List<String> suggestions = new ArrayList<>(interests(limit / 2));
        for (String topic : DEFAULT_TOPICS) {
            if (suggestions.size() >= limit) {
                break;
            }
            if (!suggestions.contains(topic)) {
                suggestions.add(topic);
            }
        }
        return suggestions;
    }

    /**
     * Searches the home feed pulls from, in the order it uses them: pairs of the player's own interests
     * (so the results look like the things they pick, not just one of them) mixed with shuffled seeds.
     */
    public static List<String> feedQueries() {
        List<String> interests = interests(6);
        Set<String> queries = new LinkedHashSet<>(interests);
        for (int i = 0; i + 1 < interests.size(); i += 2) {
            queries.add(interests.get(i) + " " + interests.get(i + 1));
        }
        List<String> seeds = new ArrayList<>(DEFAULT_TOPICS);
        Collections.shuffle(seeds);
        queries.addAll(seeds);
        return List.copyOf(queries);
    }

    private static void bump(String word, int weight) {
        weights.merge(word, weight, Integer::sum);
    }

    /** Keeps only the heaviest interests, so an old phase cannot hold the feed forever. */
    private static void prune() {
        if (weights.size() <= MAX_INTERESTS) {
            return;
        }
        List<Map.Entry<String, Integer>> kept = new ArrayList<>(weights.entrySet());
        kept.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
        Map<String, Integer> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : kept.subList(0, MAX_INTERESTS)) {
            trimmed.put(entry.getKey(), entry.getValue());
        }
        weights = trimmed;
    }

    /** Splits text into plain lower-case words worth remembering. */
    private static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String part : text.toLowerCase().split("[^a-z0-9]+")) {
            if (part.length() >= 3 && part.length() <= MAX_WORD_LENGTH && !IGNORED.contains(part)) {
                words.add(part);
            }
        }
        return words;
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
            Map<String, Integer> stored = GSON.fromJson(
                    reader, new TypeToken<LinkedHashMap<String, Integer>>() {}.getType());
            if (stored != null) {
                // Hand-edited files are normalised the same way new words are.
                stored.forEach((word, weight) -> {
                    List<String> parsed = words(word);
                    if (parsed.size() == 1 && weight != null && weight > 0) {
                        weights.put(parsed.getFirst(), Math.min(weight, 10_000));
                    }
                });
                prune();
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the taste profile", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(weights, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save the taste profile", e);
        }
    }
}
