package net.koiduu.pinspo.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.koiduu.pinspo.PinButton;
import net.koiduu.pinspo.PinTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Name, key combo, mode, timing and up to six lines for one macro. */
public class MacroEditScreen extends Screen {

    private static final int MAX_DELAY = 100;
    private static final int MAX_INTERVAL = 1200;

    @Nullable
    private final Screen parent;
    private final Macros.Macro macro;
    private final boolean isNew;
    private int key;
    private boolean ctrl;
    private boolean shift;
    private boolean alt;
    private Macros.Mode mode;
    private int lineDelay;
    private int interval;
    private boolean capturing;

    @Nullable
    private EditBox name;
    private final EditBox[] lines = new EditBox[Macros.MAX_LINES];
    private PinButton keyButton;
    private int panelX;
    private int panelWidth;
    private int linesTop;

    public MacroEditScreen(@Nullable Screen parent, @Nullable Macros.Macro existing) {
        super(Component.translatable("screen.pinspo.macro_edit"));
        this.parent = parent;
        this.isNew = existing == null;
        this.macro = existing != null ? existing : new Macros.Macro();
        key = macro.key;
        ctrl = macro.ctrl;
        shift = macro.shift;
        alt = macro.alt;
        mode = macro.mode;
        lineDelay = macro.lineDelay;
        interval = macro.interval;
    }

    @Override
    protected void init() {
        boolean first = name == null;
        panelWidth = Math.min(width - 24, 420);
        panelX = (width - panelWidth) / 2;
        int inner = panelWidth - 16;
        int x = panelX + 8;
        int quarter = Math.min(110, (inner - 8) / 4);
        int nameWidth = inner - quarter * 2 - 8;
        int half = (inner - 4) / 2;

        int y = 36;
        name = new EditBox(font, x, y + 1, nameWidth, 18, name, Component.translatable("screen.pinspo.macro_name"));
        name.setMaxLength(40);
        name.setHint(Component.translatable("screen.pinspo.macro_name_hint"));
        if (first) {
            name.setValue(macro.name);
        }
        addRenderableWidget(name);
        keyButton = PinButton.of(x + nameWidth + 4, y, quarter, 20, keyLabel(), () -> {
            capturing = true;
            keyButton.setMessage(Component.translatable("screen.pinspo.macro_press"));
        });
        addRenderableWidget(keyButton);
        addRenderableWidget(CycleButton
                .builder((Macros.Mode value) -> Component.translatable("macro.pinspo.mode." + value.name().toLowerCase(Locale.ROOT)), mode)
                .withValues(Macros.Mode.values())
                .displayOnlyValue()
                .create(x + nameWidth + quarter + 8, y, quarter, 20, Component.translatable("screen.pinspo.macro_mode"),
                        (button, value) -> mode = value));

        y += 24;
        addRenderableWidget(new AbstractSliderButton(x, y, half, 20, Component.empty(),
                (lineDelay - Macros.MIN_LINE_DELAY) / (double) (MAX_DELAY - Macros.MIN_LINE_DELAY)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("screen.pinspo.macro_delay", seconds(lineDelay)));
            }

            @Override
            protected void applyValue() {
                lineDelay = Macros.MIN_LINE_DELAY + (int) Math.round(value * (MAX_DELAY - Macros.MIN_LINE_DELAY));
            }
        });
        addRenderableWidget(new AbstractSliderButton(x + half + 4, y, half, 20, Component.empty(),
                (interval - Macros.MIN_INTERVAL) / (double) (MAX_INTERVAL - Macros.MIN_INTERVAL)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("screen.pinspo.macro_interval", seconds(interval)));
            }

            @Override
            protected void applyValue() {
                // Whole seconds past the minimum, so the slider lands on round numbers.
                interval = Macros.MIN_INTERVAL + 20 * (int) Math.round(value * (MAX_INTERVAL - Macros.MIN_INTERVAL) / 20.0);
            }
        });

        y += 36;
        linesTop = y - 12;
        for (int i = 0; i < lines.length; i++) {
            EditBox box = new EditBox(font, x, y, inner, 16, lines[i], Component.translatable("screen.pinspo.macro_lines"));
            box.setMaxLength(256);
            if (i == 0) {
                box.setHint(Component.translatable("screen.pinspo.macro_line_hint"));
            }
            if (first && i < macro.lines.size()) {
                box.setValue(macro.lines.get(i));
            }
            lines[i] = box;
            addRenderableWidget(box);
            y += 19;
        }

        int buttonY = height - 28;
        int third = (inner - 8) / 3;
        addRenderableWidget(PinButton.primary(x, buttonY, third, 20, Component.translatable("screen.pinspo.macro_save"), this::save));
        PinButton delete = PinButton.of(x + third + 4, buttonY, third, 20, Component.translatable("screen.pinspo.macro_delete"), () -> {
            macro.running = false;
            Macros.all().remove(macro);
            Macros.save();
            onClose();
        });
        delete.active = !isNew;
        addRenderableWidget(delete);
        addRenderableWidget(PinButton.of(x + (third + 4) * 2, buttonY, third, 20, Component.translatable("gui.cancel"), this::onClose));
        if (first) {
            setInitialFocus(name);
        }
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    private Component keyLabel() {
        return Component.translatable("screen.pinspo.macro_key", Macros.keyName(key, ctrl, shift, alt));
    }

    private static boolean isModifier(int value) {
        return value == InputConstants.KEY_LCONTROL || value == InputConstants.KEY_RCONTROL
                || value == InputConstants.KEY_LSHIFT || value == InputConstants.KEY_RSHIFT
                || value == InputConstants.KEY_LALT || value == InputConstants.KEY_RALT;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                key = -1;
            } else if (isModifier(event.key())) {
                return true;
            } else {
                key = event.key();
                ctrl = event.hasControlDown();
                shift = event.hasShiftDown();
                alt = event.hasAltDown();
            }
            capturing = false;
            keyButton.setMessage(keyLabel());
            return true;
        }
        return super.keyPressed(event);
    }

    private void save() {
        String typed = name.getValue().strip();
        if (isNew && Macros.all().size() >= Macros.MAX_MACROS) {
            onClose();
            return;
        }
        macro.name = typed.isEmpty() ? Component.translatable("screen.pinspo.macro_default", Macros.all().size() + (isNew ? 1 : 0)).getString() : typed;
        macro.key = key;
        macro.ctrl = ctrl;
        macro.shift = shift;
        macro.alt = alt;
        macro.mode = mode;
        macro.lineDelay = lineDelay;
        macro.interval = interval;
        macro.running = false;
        List<String> kept = new ArrayList<>();
        for (EditBox box : lines) {
            if (!box.getValue().isBlank()) {
                kept.add(box.getValue().strip());
            }
        }
        macro.lines = kept;
        if (isNew) {
            Macros.all().add(macro);
        }
        Macros.save();
        net.koiduu.pinspo.PinSpoClient.LOGGER.info("Saved macro '{}' on {} ({}, {} lines)", macro.name,
                macro.keyName().getString(), macro.mode, kept.size());
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        PinTheme.panel(guiGraphics, panelX, 10, panelWidth, height - 20);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        int x = panelX + 8;
        PinTheme.sectionHeader(guiGraphics, font, title, x, 18);
        guiGraphics.text(font, Component.translatable("screen.pinspo.macro_lines"), x, linesTop, PinTheme.TEXT_MUTED, false);
        int hintY = linesTop + 12 + lines.length * 19 + 2;
        for (var line : font.split(Component.translatable("screen.pinspo.macro_hint"), panelWidth - 16)) {
            if (hintY + 10 > height - 30) {
                break;
            }
            guiGraphics.text(font, line, x, hintY, PinTheme.TEXT_MUTED, false);
            hintY += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
