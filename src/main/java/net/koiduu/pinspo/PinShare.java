package net.koiduu.pinspo;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Share codes. A reference is identified by nothing but the hex filename Pinterest gives it, so a code is
 * short enough to fit in a chat message and cannot carry an arbitrary URL:
 *
 * <pre>PinSpo&gt;Name&gt;hash.jpg hash.jpg ...</pre>
 *
 * Both image URLs are rebuilt locally from the hash, because Pinterest lays its CDN paths out as
 * {@code /<size>/<h0h1>/<h2h3>/<h4h5>/<hash>.<ext>}.
 */
public final class PinShare {

    private static final String PREFIX = "PinSpo>";
    private static final String THUMBNAIL_SIZE = "236x";
    private static final String FULL_SIZE = "736x";
    /** One code carries at most this many references, so a hostile code cannot exhaust memory. */
    private static final int MAX_PINS = 60;
    private static final Pattern IMAGE = Pattern.compile("([0-9a-f]{6,64})\\.([a-z]{3,4})");
    private static final Pattern CODE = Pattern.compile(
            Pattern.quote(PREFIX) + "([^>\\s]{0,32})>((?:[0-9a-f]{6,64}\\.[a-z]{3,4}[ ]?)+)");

    /** A shared folder: its name and the pins in it. */
    public record Shared(String name, List<PinterestApi.Pin> pins) {
    }

    private PinShare() {
    }

    public static String encode(String name, List<PinterestApi.Pin> pins) {
        StringBuilder code = new StringBuilder(PREFIX).append(PinSecurity.cleanName(name).replace('>', ' '))
                .append('>');
        int written = 0;
        for (PinterestApi.Pin pin : pins) {
            String image = imageName(pin.imageUrl());
            if (image == null) {
                continue;
            }
            if (written++ > 0) {
                code.append(' ');
            }
            code.append(image);
            if (written == MAX_PINS) {
                break;
            }
        }
        return code.toString();
    }

    /** Parses the first share code found in {@code text}, or {@code null} when there is none. */
    @Nullable
    public static Shared decode(String text) {
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        List<PinterestApi.Pin> pins = new ArrayList<>();
        Matcher images = IMAGE.matcher(matcher.group(2));
        while (images.find() && pins.size() < MAX_PINS) {
            PinterestApi.Pin pin = toPin(images.group(1), images.group(2));
            if (pin != null) {
                pins.add(pin);
            }
        }
        if (pins.isEmpty()) {
            return null;
        }
        return new Shared(PinSecurity.cleanName(matcher.group(1)), pins);
    }

    /**
     * True when a pin can travel in a share code at all. Only Pinterest references can: a local file or a
     * link from elsewhere has nothing the other client could rebuild.
     */
    public static boolean isShareable(PinterestApi.Pin pin) {
        return imageName(pin.imageUrl()) != null;
    }

    /** True when {@code text} contains a share code, used to spot PinSpo messages in chat. */
    public static boolean looksLikeCode(String text) {
        return CODE.matcher(text).find();
    }

    /** Rebuilds a pin from a hash, rejecting anything that does not produce two allowed image URLs. */
    @Nullable
    private static PinterestApi.Pin toPin(String hash, String extension) {
        if (!PinSecurity.isImageHash(hash) || !PinSecurity.isImageExtension(extension)) {
            return null;
        }
        String tail = "%s/%s/%s/%s.%s".formatted(
                hash.substring(0, 2), hash.substring(2, 4), hash.substring(4, 6), hash, extension);
        PinterestApi.Pin pin = new PinterestApi.Pin(
                hash,
                "Shared reference",
                "https://i.pinimg.com/" + THUMBNAIL_SIZE + "/" + tail,
                "https://i.pinimg.com/" + FULL_SIZE + "/" + tail,
                0,
                0);
        return PinSecurity.isAllowedPin(pin) ? pin : null;
    }

    /** The {@code hash.ext} part of a Pinterest image URL, or {@code null} if it is not one. */
    @Nullable
    public static String imageName(String url) {
        if (!PinSecurity.isAllowedImageUrl(url)) {
            return null;
        }
        Matcher matcher = IMAGE.matcher(url.substring(url.lastIndexOf('/') + 1));
        return matcher.matches() ? matcher.group() : null;
    }

    /** Imports a share code into a saved folder, returning the folder name it landed in. */
    @Nullable
    public static String importCode(String code) {
        Shared shared = decode(code);
        if (shared == null) {
            return null;
        }
        String folder = shared.name().isBlank() ? "Shared" : shared.name();
        SavedPins.createFolder(folder);
        for (PinterestApi.Pin pin : shared.pins()) {
            SavedPins.add(folder, pin);
        }
        return folder;
    }
}
