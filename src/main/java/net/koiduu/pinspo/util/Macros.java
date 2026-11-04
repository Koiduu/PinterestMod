package net.koiduu.pinspo.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.koiduu.pinspo.PinSpoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keys that type for you. A macro is a few lines (chat, or commands starting with /) bound to a key combo,
 * sent once, repeated while toggled on, or repeated while the key is held.
 */
public final class Macros {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-macros.json");
    public static final int MAX_MACROS = 12;
    public static final int MAX_LINES = 6;
    /** Floors that keep a macro from getting the player kicked for spam. */
    public static final int MIN_LINE_DELAY = 4;
    public static final int MIN_INTERVAL = 20;

    public enum Mode {
        ONCE, TOGGLE, HOLD
    }

    public static final class Macro {
        public String name = "";
        public int key = -1;
        public boolean ctrl;
        public boolean shift;
        public boolean alt;
        public Mode mode = Mode.ONCE;
        /** Ticks between lines. */
        public int lineDelay = 10;
        /** Ticks between repeats in toggle and hold modes. */
        public int interval = 40;
        public List<String> lines = new ArrayList<>();

        transient boolean wasDown;
        transient boolean running;
        transient int next;
        transient int wait;

        public boolean isRunning() {
            return running;
        }

        public Component keyName() {
            return Macros.keyName(key, ctrl, shift, alt);
        }

        void clamp() {
            if (name == null) {
                name = "";
            }
            if (mode == null) {
                mode = Mode.ONCE;
            }
            if (lines == null) {
                lines = new ArrayList<>();
            }
            lines.removeIf(line -> line == null);
            while (lines.size() > MAX_LINES) {
                lines.removeLast();
            }
            lineDelay = Math.clamp(lineDelay, MIN_LINE_DELAY, 200);
            interval = Math.clamp(interval, MIN_INTERVAL, 20 * 60 * 10);
        }
    }

    private static List<Macro> stored;
    /** Key presses since the last tick, straight from the keyboard events. */
    private static final List<net.minecraft.client.input.KeyEvent> PRESSES = new ArrayList<>();

    public static void onKeyPress(net.minecraft.client.input.KeyEvent event) {
        if (Minecraft.getInstance().gui.screen() == null && PRESSES.size() < 32) {
            PRESSES.add(event);
        }
    }

    public static Component keyName(int key, boolean ctrl, boolean shift, boolean alt) {
        if (key <= 0) {
            return Component.translatable("macro.pinspo.no_key");
        }
        String combo = (ctrl ? "Ctrl + " : "") + (shift ? "Shift + " : "") + (alt ? "Alt + " : "");
        return Component.literal(combo).append(InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName());
    }

    private Macros() {
    }

    public static List<Macro> all() {
        if (stored == null) {
            stored = new ArrayList<>();
            if (Files.isRegularFile(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    List<Macro> loaded = GSON.fromJson(reader, new TypeToken<List<Macro>>() { }.getType());
                    if (loaded != null) {
                        loaded.removeIf(macro -> macro == null);
                        loaded.forEach(Macro::clamp);
                        stored.addAll(loaded);
                    }
                } catch (Exception e) {
                    PinSpoClient.LOGGER.warn("Could not read macros", e);
                }
            }
        }
        return stored;
    }

    public static void save() {
        all().forEach(Macro::clamp);
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(all(), writer);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not save macros", e);
        }
    }

    public static void stopAll() {
        for (Macro macro : all()) {
            macro.running = false;
        }
    }

    private static boolean down(int... keys) {
        for (int key : keys) {
            if (InputConstants.isKeyDown(key)) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            stopAll();
            PRESSES.clear();
            return;
        }
        boolean free = client.gui.screen() == null;
        boolean ctrl = down(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
        boolean shift = down(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
        boolean alt = down(InputConstants.KEY_LALT, InputConstants.KEY_RALT);
        for (Macro macro : all()) {
            boolean tapped = macro.key > 0 && PRESSES.stream().anyMatch(event -> event.key() == macro.key
                    && event.hasControlDown() == macro.ctrl && event.hasShiftDown() == macro.shift
                    && event.hasAltDown() == macro.alt);
            boolean held = free && macro.key > 0 && InputConstants.isKeyDown(macro.key)
                    && macro.ctrl == ctrl && macro.shift == shift && macro.alt == alt;
            boolean isDown = held || tapped;
            boolean pressed = tapped || (held && !macro.wasDown);
            macro.wasDown = held;
            switch (macro.mode) {
                case ONCE -> {
                    if (pressed && !macro.running) {
                        start(macro);
                    }
                }
                case TOGGLE -> {
                    if (pressed) {
                        if (macro.running) {
                            macro.running = false;
                            tell(Component.translatable("macro.pinspo.stopped", macro.name));
                        } else {
                            start(macro);
                            tell(Component.translatable("macro.pinspo.started", macro.name, macro.keyName()));
                        }
                    }
                }
                case HOLD -> {
                    if (isDown && !macro.running) {
                        start(macro);
                    } else if (!isDown) {
                        macro.running = false;
                    }
                }
            }
            if (macro.running) {
                run(client, macro);
            }
        }
        PRESSES.clear();
    }

    private static void start(Macro macro) {
        macro.running = !macro.lines.isEmpty();
        macro.next = 0;
        macro.wait = 0;
        PinSpoClient.LOGGER.info("Macro '{}' started ({})", macro.name, macro.mode);
    }

    private static void run(Minecraft client, Macro macro) {
        if (macro.wait > 0) {
            macro.wait--;
            return;
        }
        if (macro.next >= macro.lines.size()) {
            if (macro.mode == Mode.ONCE) {
                macro.running = false;
                return;
            }
            macro.next = 0;
        }
        send(client, macro.lines.get(macro.next++));
        macro.wait = macro.next >= macro.lines.size() ? Math.max(macro.interval, macro.lineDelay) : macro.lineDelay;
    }

    private static void send(Minecraft client, String raw) {
        String line = raw.strip();
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("/")) {
            client.getConnection().sendCommand(line.substring(1));
        } else {
            client.getConnection().sendChat(line);
        }
    }

    private static void tell(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }
}
