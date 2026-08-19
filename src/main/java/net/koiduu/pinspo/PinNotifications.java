package net.koiduu.pinspo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * PinSpo's own little popups. Friend requests and incoming references used to be visible only inside the
 * Friends tab, so this announces them on the HUD (and over the PinSpo screens) the moment they arrive.
 */
public final class PinNotifications {

    private static final int WIDTH = 152;
    private static final int HEIGHT = 30;
    private static final int MARGIN = 6;
    private static final long LIFETIME_MS = 7_000L;
    private static final long FADE_MS = 400L;
    private static final int MAX_SHOWN = 3;

    private record Popup(Component title, Component detail, long shownAt) {
    }

    private static final Deque<Popup> popups = new ArrayDeque<>();

    private PinNotifications() {
    }

    /** {@code name} wants to be friends: the request itself is answered in the Friends tab. */
    public static void friendRequest(String name) {
        show(Component.translatable("notify.pinspo.friend_request", name),
                Component.translatable("notify.pinspo.answer_in_friends"));
    }

    public static void friendAccepted(String name) {
        show(Component.translatable("notify.pinspo.friend_accepted", name),
                Component.translatable("notify.pinspo.open_friends"));
    }

    public static void reference(String name) {
        show(Component.translatable("notify.pinspo.reference", name),
                Component.translatable("notify.pinspo.open_friends"));
    }

    public static void message(String name) {
        show(Component.translatable("notify.pinspo.message", name),
                Component.translatable("notify.pinspo.open_friends"));
    }

    /** The server refused to carry what PinSpo sent, e.g. Hypixel's advertising filter. */
    public static void sendBlocked() {
        show(Component.translatable("notify.pinspo.send_blocked"),
                Component.translatable("notify.pinspo.send_blocked_hint"));
    }

    private static void show(Component title, Component detail) {
        popups.addLast(new Popup(title, detail, Util.getMillis()));
        while (popups.size() > MAX_SHOWN) {
            popups.removeFirst();
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.4F, 0.3F));
    }

    /** Draws the live popups down the right-hand side, newest on top. */
    public static void render(GuiGraphicsExtractor guiGraphics, int screenWidth) {
        if (popups.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        popups.removeIf(popup -> now - popup.shownAt() > LIFETIME_MS);
        var font = Minecraft.getInstance().font;
        int y = MARGIN;
        for (Popup popup : popups) {
            long age = now - popup.shownAt();
            // Slides in from the right, then slides back out over its last moments.
            long remaining = LIFETIME_MS - age;
            float slide = age < FADE_MS
                    ? 1.0F - age / (float) FADE_MS
                    : remaining < FADE_MS ? 1.0F - remaining / (float) FADE_MS : 0.0F;
            int x = screenWidth - MARGIN - WIDTH + Math.round(slide * (WIDTH + MARGIN));
            PinTheme.panel(guiGraphics, x, y, WIDTH, HEIGHT);
            guiGraphics.fill(x, y, x + 2, y + HEIGHT, PinTheme.ACCENT);
            guiGraphics.text(font,
                    font.plainSubstrByWidth(popup.title().getString(), WIDTH - 12),
                    x + 7, y + 6, PinTheme.TEXT, false);
            guiGraphics.text(font,
                    font.plainSubstrByWidth(popup.detail().getString(), WIDTH - 12),
                    x + 7, y + 18, PinTheme.TEXT_MUTED, false);
            y += HEIGHT + 4;
        }
    }
}
