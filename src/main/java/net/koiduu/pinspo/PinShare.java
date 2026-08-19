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

    /**
     * The chat form of a code. Servers with an advertising filter — Hypixel's especially — swallow a
     * message containing {@code hash.jpg}, because it reads as a domain, so nothing sent this way carries a
     * dot: the extension becomes a single letter and the hash travels in short groups.
     */
    private static final String CHAT_PREFIX = "PinSpo ref ";
    private static final Pattern CHAT_CODE = Pattern.compile(
            Pattern.quote(CHAT_PREFIX) + "([jpwg]) ((?:[0-9a-f]{1,8}(?: |$)){1,12})");
    private static final int GROUP_LENGTH = 8;
    /** Extension letters, kept deliberately tiny so a code stays short and unremarkable. */
    private static final String[][] EXTENSIONS = {
            {"j", "jpg"}, {"p", "png"}, {"w", "webp"}, {"g", "gif"}
    };

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

    /**
     * A reference written so a chat filter leaves it alone, or {@code ""} when this pin cannot travel.
     * Only one reference fits per message, which is all a send to a friend ever needs.
     */
    public static String encodeChat(PinterestApi.Pin pin) {
        String image = imageName(pin.imageUrl());
        if (image == null) {
            return "";
        }
        int dot = image.lastIndexOf('.');
        String letter = letterFor(image.substring(dot + 1));
        if (letter == null) {
            return "";
        }
        String hash = image.substring(0, dot);
        StringBuilder code = new StringBuilder(CHAT_PREFIX).append(letter);
        for (int at = 0; at < hash.length(); at += GROUP_LENGTH) {
            code.append(' ').append(hash, at, Math.min(hash.length(), at + GROUP_LENGTH));
        }
        return code.toString();
    }

    /** The reference in a chat-form code, or {@code null} when the message does not hold one. */
    @Nullable
    private static Shared decodeChat(String text) {
        Matcher matcher = CHAT_CODE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String extension = extensionFor(matcher.group(1));
        if (extension == null) {
            return null;
        }
        PinterestApi.Pin pin = toPin(matcher.group(2).replace(" ", ""), extension);
        return pin == null ? null : new Shared("Shared", List.of(pin));
    }

    @Nullable
    private static String letterFor(String extension) {
        for (String[] pair : EXTENSIONS) {
            if (pair[1].equals(extension)) {
                return pair[0];
            }
        }
        return null;
    }

    @Nullable
    private static String extensionFor(String letter) {
        for (String[] pair : EXTENSIONS) {
            if (pair[0].equals(letter)) {
                return pair[1];
            }
        }
        return null;
    }

    /** Parses the first share code found in {@code text}, or {@code null} when there is none. */
    @Nullable
    public static Shared decode(String text) {
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) {
            // Older PinSpo builds and clipboard codes use the long form; chat now uses the quiet one.
            return decodeChat(text);
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
        return CODE.matcher(text).find() || CHAT_CODE.matcher(text).find();
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
