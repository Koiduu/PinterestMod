package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Images that come from the player's own computer rather than Pinterest: files they pick from disk and
 * their Minecraft screenshots. Both are addressed with a {@code pinspo:} URL naming a single file inside
 * one of two known folders, so a stored pin can never point somewhere else on disk.
 */
public final class LocalImages {

    private static final String IMPORTED_PREFIX = "pinspo:local/";
    private static final String SCREENSHOT_PREFIX = "pinspo:shot/";
    private static final Path IMPORT_DIR = FabricLoader.getInstance().getConfigDir().resolve("pinspo/local");
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    /** Enough screenshots to pick a recent one from without turning the grid into a file browser. */
    private static final int MAX_LISTED = 60;

    private LocalImages() {
    }

    public static boolean isLocalUrl(String url) {
        return fileOf(url) != null;
    }

    /** The file a {@code pinspo:} URL names, or {@code null} when the URL is not a valid local one. */
    @Nullable
    public static Path fileOf(String url) {
        if (url == null) {
            return null;
        }
        String name;
        Path directory;
        if (url.startsWith(IMPORTED_PREFIX)) {
            name = url.substring(IMPORTED_PREFIX.length());
            directory = IMPORT_DIR;
        } else if (url.startsWith(SCREENSHOT_PREFIX)) {
            name = url.substring(SCREENSHOT_PREFIX.length());
            directory = screenshotDir();
        } else {
            return null;
        }
        return PinSecurity.isLocalFileName(name) ? directory.resolve(name) : null;
    }

    /** Reads a local image's bytes, or {@code null} when the file is gone or too large. */
    @Nullable
    public static byte[] read(String url) {
        Path file = fileOf(url);
        try {
            if (file == null || !Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not read local image {}", url, e);
            return null;
        }
    }

    /**
     * Copies {@code file} into PinSpo's own folder so the pin keeps working after the original moves, and
     * returns it as a pin. Returns {@code null} when the file is not a readable image.
     */
    @Nullable
    public static PinterestApi.Pin importFile(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            String extension = extensionOf(file.getFileName().toString());
            if (extension == null) {
                return null;
            }
            try (NativeImage decoded = ImageDecoder.decode(bytes)) {
                if (decoded.getWidth() <= 0) {
                    return null;
                }
            }
            String name = digest(bytes) + "." + extension;
            Files.createDirectories(IMPORT_DIR);
            Path target = IMPORT_DIR.resolve(name);
            if (!Files.isRegularFile(target)) {
                Files.write(target, bytes);
            }
            return pinOf(IMPORTED_PREFIX + name, file.getFileName().toString());
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not import {}", file, e);
            return null;
        }
    }

    /** Imported files first, then the newest screenshots, so both show up in one grid. */
    public static List<PinterestApi.Pin> browsable() {
        List<PinterestApi.Pin> pins = new ArrayList<>(list(IMPORT_DIR, IMPORTED_PREFIX));
        pins.addAll(list(screenshotDir(), SCREENSHOT_PREFIX));
        return pins;
    }

    private static List<PinterestApi.Pin> list(Path directory, String prefix) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> PinSecurity.isLocalFileName(path.getFileName().toString()))
                    .sorted(Comparator.comparingLong(LocalImages::modifiedAt).reversed())
                    .limit(MAX_LISTED)
                    .map(path -> {
                        String name = path.getFileName().toString();
                        return pinOf(prefix + name, name);
                    })
                    .toList();
        } catch (IOException e) {
            PinSpoClient.LOGGER.warn("Could not list {}", directory, e);
            return List.of();
        }
    }

    private static long modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static PinterestApi.Pin pinOf(String url, String title) {
        return new PinterestApi.Pin(url, PinSecurity.cleanName(title), url, url, 0, 0);
    }

    @Nullable
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase();
        return PinSecurity.isImageExtension(extension) ? extension : null;
    }

    private static String digest(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            name.append("%02x".formatted(hash[index]));
        }
        return name.toString();
    }

    private static Path screenshotDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }
}
