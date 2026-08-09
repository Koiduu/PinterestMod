package net.koiduu.pinspo;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Everything PinSpo accepts from outside the game — share codes, chat messages, pin URLs, folder names —
 * passes through here first. Nothing else is allowed to download, name a file, or build a command.
 */
public final class PinSecurity {

    /** Images may only ever be fetched from Pinterest's own image CDN. */
    private static final String IMAGE_HOST_SUFFIX = ".pinimg.com";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    /** The hex filename Pinterest gives every image; also the only thing a share code carries. */
    private static final Pattern IMAGE_HASH = Pattern.compile("[0-9a-f]{6,64}");
    private static final Pattern IMAGE_EXTENSION = Pattern.compile("jpg|jpeg|png|webp|gif");
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_TEXT_LENGTH = 200;

    private PinSecurity() {
    }

    /**
     * True only for {@code https} URLs on Pinterest's image CDN with an image extension, so a crafted
     * share code cannot make the client fetch an arbitrary address.
     */
    public static boolean isAllowedImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || path == null) {
                return false;
            }
            host = host.toLowerCase();
            if (!host.endsWith(IMAGE_HOST_SUFFIX) || uri.getUserInfo() != null) {
                return false;
            }
            int dot = path.lastIndexOf('.');
            return dot > 0 && IMAGE_EXTENSION.matcher(path.substring(dot + 1).toLowerCase()).matches();
        } catch (Exception e) {
            return false;
        }
    }

    /** True for a pin that is safe to store, download and show. */
    public static boolean isAllowedPin(PinterestApi.Pin pin) {
        return pin != null
                && pin.imageUrl() != null && pin.thumbnailUrl() != null
                && isAllowedImageUrl(pin.imageUrl())
                && isAllowedImageUrl(pin.thumbnailUrl());
    }

    public static boolean isImageHash(String hash) {
        return IMAGE_HASH.matcher(hash).matches();
    }

    public static boolean isImageExtension(String extension) {
        return IMAGE_EXTENSION.matcher(extension).matches();
    }

    /** Vanilla-shaped Minecraft names only, which also keeps names out of command arguments. */
    public static boolean isPlayerName(String name) {
        return PLAYER_NAME.matcher(name).matches();
    }

    /**
     * A folder or friend label that is safe to use as a map key and to show: no control characters, no
     * section signs, no path separators, and bounded in length.
     */
    public static String cleanName(String raw) {
        String cleaned = clean(raw, MAX_NAME_LENGTH).replace('/', ' ').replace('\\', ' ');
        return cleaned.isBlank() ? "" : cleaned;
    }

    /** Free text received from another player: shown as-is, so formatting codes are stripped. */
    public static String cleanText(String raw) {
        return clean(raw, MAX_TEXT_LENGTH);
    }

    private static String clean(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(raw.length(), maxLength));
        for (int index = 0; index < raw.length() && cleaned.length() < maxLength; index++) {
            char character = raw.charAt(index);
            if (character == 167 || character < ' ' || character == 127) {
                continue;
            }
            cleaned.append(character);
        }
        return cleaned.toString().trim();
    }
}
