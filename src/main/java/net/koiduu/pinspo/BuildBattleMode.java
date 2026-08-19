package net.koiduu.pinspo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches chat for Hypixel Build Battle rounds: searches Pinterest for the theme as soon as it is
 * announced, and hides the reference overlay again once voting starts.
 */
public final class BuildBattleMode {

    private static final Pattern THEME = Pattern.compile(
            "(?i)(?:the\\s+)?theme\\s*(?:is|was)?\\s*[:\\-]\\s*(.+)");
    private static final Pattern VOTING = Pattern.compile(
            "(?i)voting has (?:begun|started)|you are now voting|the round has ended|vote for");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!.\\s]+$");

    @Nullable
    private static String currentTheme;

    private BuildBattleMode() {
    }

    @Nullable
    public static String currentTheme() {
        return currentTheme;
    }

    /** Handles one chat line; called for every game message the client receives. */
    public static void onChatMessage(Component message) {
        if (!PinSpoConfig.get().buildBattleMode) {
            return;
        }
        String text = message.getString();
        if (VOTING.matcher(text).find()) {
            currentTheme = null;
            PinnedImage.setHidden(true);
            return;
        }
        Matcher matcher = THEME.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String theme = TRAILING_PUNCTUATION.matcher(matcher.group(1).trim()).replaceAll("");
        if (theme.isEmpty() || theme.equalsIgnoreCase(currentTheme)) {
            return;
        }
        currentTheme = theme;
        PinnedImage.setHidden(false);
        onThemeChosen(theme);
    }

    private static void onThemeChosen(String theme) {
        Minecraft client = Minecraft.getInstance();
        if (PinSpoConfig.get().buildBattleRandomPin) {
            pinRandom(theme);
            return;
        }
        if (client.screen == null) {
            client.setScreen(new PinBrowseScreen(null, theme));
        }
    }

    /** Pins a random result for {@code theme} without opening any screen. */
    public static void pinRandom(String theme) {
        Minecraft client = Minecraft.getInstance();
        PinterestApi.search(theme, null).whenComplete((page, throwable) -> client.execute(() -> {
            if (throwable != null || page == null || page.pins().isEmpty()) {
                PinSpoClient.LOGGER.warn("No Build Battle results for theme '{}'", theme, throwable);
                notifyPlayer(Component.translatable("message.pinspo.no_results", theme));
                return;
            }
            List<PinterestApi.Pin> pins = page.pins();
            PinterestApi.Pin pin = pins.get(ThreadLocalRandom.current().nextInt(pins.size()));
            PinnedImage.pin(pin);
            notifyPlayer(Component.translatable("message.pinspo.random_pinned", theme));
        }));
    }

    /** Resets the round state, e.g. when leaving a server. */
    public static void reset() {
        currentTheme = null;
    }

    private static void notifyPlayer(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, true);
        }
    }
}
