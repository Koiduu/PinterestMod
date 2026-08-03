package net.koiduu.pinspo;

import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Share codes: a folder of references packed into one clipboard-sized string, so players can send
 * each other references without PinSpo needing a server of its own.
 */
public final class PinShare {

    private static final Gson GSON = new Gson();
    private static final String PREFIX = "PINSPO1:";

    /** A shared folder: its name and the pins in it. */
    public record Shared(String name, List<PinterestApi.Pin> pins) {
    }

    private PinShare() {
    }

    public static String encode(String name, List<PinterestApi.Pin> pins) {
        byte[] json = GSON.toJson(new Shared(name, pins)).getBytes(StandardCharsets.UTF_8);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    /** Parses a share code, returning {@code null} when the text is not a valid PinSpo code. */
    @Nullable
    public static Shared decode(String code) {
        String trimmed = code.trim();
        if (!trimmed.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
            Shared shared = GSON.fromJson(new String(json, StandardCharsets.UTF_8), Shared.class);
            if (shared == null || shared.pins() == null || shared.pins().isEmpty()) {
                return null;
            }
            return shared;
        } catch (Exception e) {
            PinSpoClient.LOGGER.debug("Ignoring an unreadable share code", e);
            return null;
        }
    }

    /** Imports a share code into a saved folder, returning the folder name it landed in. */
    @Nullable
    public static String importCode(String code) {
        Shared shared = decode(code);
        if (shared == null) {
            return null;
        }
        String folder = shared.name() == null || shared.name().isBlank()
                ? "Shared"
                : shared.name();
        SavedPins.createFolder(folder);
        for (PinterestApi.Pin pin : shared.pins()) {
            SavedPins.add(folder, pin);
        }
        return folder;
    }
}
