package net.koiduu.pinspo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
 * What the player keeps picking, kept two ways: overall interests that steer the home feed and the search
 * suggestions, and a memory per search of the pins they chose out of it, so searching the same thing again
 * puts results like the ones they picked before at the top. It is the useful half of having an account,
 * without one — nothing leaves the machine.
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

    /** Searches remembered in detail, oldest dropped first once the list is full. */
    private static final int MAX_QUERIES = 40;
    /** Words remembered per search, so one busy search cannot grow without limit. */
    private static final int MAX_QUERY_WORDS = 24;
    /** A word learnt from this exact search says far more than a general interest does. */
    private static final int QUERY_MATCH_FACTOR = 4;
    /** Caps a single word's pull, so one much-used word cannot decide the whole page's order. */
    private static final int MAX_WORD_PULL = 8;
    /** Keys are normalised searches; anything longer is a sentence, not a search worth remembering. */
    private static final int MAX_KEY_LENGTH = 60;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-taste.json");

    private static Map<String, Integer> weights = new LinkedHashMap<>();
    /** Per search: the words of the pins picked out of that search, and how often. */
    private static Map<String, Map<String, Integer>> queries = new LinkedHashMap<>();
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

    /**
     * Records a pin the player picked out of {@code query}: the search itself becomes an interest, and the
     * pin's own words are remembered against that search so the next page of it can be ranked by them.
     */
    public static void recordPick(String query, PinterestApi.Pin pin) {
        load();
        String key = key(query);
        if (!key.isEmpty()) {
            List<String> picked = new ArrayList<>(words(pin.title()));
            if (pin.credit() != null) {
                picked.addAll(words(pin.credit()));
            }
            if (!picked.isEmpty()) {
                // Re-inserted, so a search used again is the last one dropped when the list is full.
                Map<String, Integer> learnt = queries.remove(key);
                if (learnt == null) {
                    learnt = new LinkedHashMap<>();
                }
                for (String word : picked) {
                    learnt.merge(word, 1, Integer::sum);
                }
                queries.put(key, trim(learnt, MAX_QUERY_WORDS));
                pruneQueries();
            }
        }
        recordQuery(query);
    }

    /**
     * Sorts a page of results so the ones closest to what the player picked before come first. Only pages
     * are reordered, never the whole grid, so results already scrolled past do not move under the cursor.
     */
    public static List<PinterestApi.Pin> rank(String query, List<PinterestApi.Pin> pins) {
        if (!PinSpoConfig.get().personalise || pins.size() < 2) {
            return pins;
        }
        load();
        if (weights.isEmpty() && queries.isEmpty()) {
            return pins;
        }
        Map<String, Integer> learnt = queries.get(key(query));
        List<PinterestApi.Pin> ranked = new ArrayList<>(pins);
        // Stable, so pins Pinterest ranked equally keep Pinterest's own order between them.
        ranked.sort(Comparator.comparingInt((PinterestApi.Pin pin) -> score(pin, learnt)).reversed());
        return ranked;
    }

    /** How much a pin looks like the player's taste: its words' learnt weights, capped word by word. */
    private static int score(PinterestApi.Pin pin, Map<String, Integer> learnt) {
        List<String> pinWords = new ArrayList<>(words(pin.title()));
        if (pin.credit() != null) {
            pinWords.addAll(words(pin.credit()));
        }
        int score = 0;
        for (String word : new LinkedHashSet<>(pinWords)) {
            score += pull(weights.get(word));
            score += QUERY_MATCH_FACTOR * pull(learnt == null ? null : learnt.get(word));
        }
        return score;
    }

    private static int pull(Integer weight) {
        return weight == null ? 0 : Math.min(weight, MAX_WORD_PULL);
    }

    /** True when there is anything learnt to forget, so the settings screen can say so. */
    public static boolean hasProfile() {
        load();
        return !weights.isEmpty() || !queries.isEmpty();
    }

    /** Throws the whole profile away: results go back to Pinterest's own order. */
    public static void forget() {
        load();
        weights = new LinkedHashMap<>();
        queries = new LinkedHashMap<>();
        save();
    }

    private static void record(String text, int weight) {
        load();
        List<String> words = words(text);
        if (words.isEmpty()) {
            return;
        }
        for (String word : words) {
            weights.merge(word, weight, Integer::sum);
        }
        weights = trim(weights, MAX_INTERESTS);
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
        Set<String> feed = new LinkedHashSet<>(interests);
        for (int i = 0; i + 1 < interests.size(); i += 2) {
            feed.add(interests.get(i) + " " + interests.get(i + 1));
        }
        List<String> seeds = new ArrayList<>(DEFAULT_TOPICS);
        Collections.shuffle(seeds);
        feed.addAll(seeds);
        return List.copyOf(feed);
    }

    /** Keeps only the heaviest entries, so an old phase cannot hold the feed forever. */
    private static Map<String, Integer> trim(Map<String, Integer> source, int limit) {
        if (source.size() <= limit) {
            return source;
        }
        List<Map.Entry<String, Integer>> kept = new ArrayList<>(source.entrySet());
        kept.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
        Map<String, Integer> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : kept.subList(0, limit)) {
            trimmed.put(entry.getKey(), entry.getValue());
        }
        return trimmed;
    }

    /** Drops the least recently used searches once too many are remembered. */
    private static void pruneQueries() {
        while (queries.size() > MAX_QUERIES) {
            queries.remove(queries.keySet().iterator().next());
        }
    }

    /** A search reduced to its meaningful words, so "Medieval Castle!" and "medieval castle" agree. */
    private static String key(String query) {
        String key = String.join(" ", words(query));
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
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
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("words") || object.has("queries")) {
                weights = readWeights(object.get("words"), MAX_INTERESTS);
                readQueries(object.get("queries"));
            } else {
                // Profiles written before searches were remembered are a bare map of interests.
                weights = readWeights(object, MAX_INTERESTS);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not read the taste profile", e);
        }
    }

    /** Hand-edited files are normalised the same way new words are, and bounded the same way. */
    private static Map<String, Integer> readWeights(JsonElement element, int limit) {
        Map<String, Integer> read = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return read;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            List<String> parsed = words(entry.getKey());
            JsonElement value = entry.getValue();
            if (parsed.size() != 1 || !(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                continue;
            }
            int weight = primitive.getAsInt();
            if (weight > 0) {
                read.put(parsed.getFirst(), Math.min(weight, 10_000));
            }
        }
        return trim(read, limit);
    }

    private static void readQueries(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = key(entry.getKey());
            Map<String, Integer> learnt = readWeights(entry.getValue(), MAX_QUERY_WORDS);
            if (!key.isEmpty() && !learnt.isEmpty()) {
                queries.put(key, learnt);
            }
        }
        pruneQueries();
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.add("words", GSON.toJsonTree(weights));
        root.add("queries", GSON.toJsonTree(queries));
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not save the taste profile", e);
        }
    }
}
