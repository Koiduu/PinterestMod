package net.koiduu.pinspo.build;

import net.koiduu.pinspo.PinButton;
import net.koiduu.pinspo.PinTabScreen;
import net.koiduu.pinspo.PinTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Tools tab: three columns, one per kind of job. Selection edits on the left, clipboard in the middle,
 * shapes and brushes on the right. Every button closes the menu so you see the result straight away.
 */
public class BuildToolsScreen extends PinTabScreen {

    private static final int ROW = 22;

    private int columnWidth;
    private int[] columns = new int[3];
    private int top;

    public BuildToolsScreen(@Nullable Screen parent) {
        super(Component.literal("Build tools"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.TOOLS;
    }

    @Override
    protected void init() {
        addTabs();
        columnWidth = Math.max(110, Math.min(190, (width - MARGIN * 2 - 48) / 3));
        int gap = (width - MARGIN * 2 - columnWidth * 3) / 4;
        for (int i = 0; i < 3; i++) {
            columns[i] = MARGIN + gap + i * (columnWidth + gap);
        }
        top = CONTENT_TOP + 32;
        int half = (columnWidth - 4) / 2;

        int x = columns[0];
        int y = top;
        addRenderableWidget(PinButton.primary(x, y, columnWidth, 20,
                Component.literal(BuildTools.isActive() ? "Build mode: on (N)" : "Build mode: off (N)"), () -> {
                    BuildTools.toggle();
                    rebuild();
                }));
        y += ROW + 4;
        action(x, y, half, "Fill", BuildTools::fill);
        action(x + half + 4, y, half, "Clear", BuildTools::clear);
        y += ROW;
        action(x, y, half, "Walls", BuildTools::walls);
        action(x + half + 4, y, half, "Hollow box", BuildTools::hollow);
        y += ROW;
        action(x, y, half, "Replace", BuildTools::replace);
        action(x + half + 4, y, half, "Stack", BuildTools::stack);
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, columnWidth, 20, Component.literal("Deselect"), () -> {
            BuildTools.clearSelection();
            rebuild();
        }));

        x = columns[1];
        y = top;
        action(x, y, half, "Copy", () -> BuildTools.copy(false));
        action(x + half + 4, y, half, "Cut", () -> BuildTools.copy(true));
        y += ROW;
        action(x, y, columnWidth, "Paste where I look", BuildTools::paste);
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, half, 20, Component.literal("Turn 90°"), () -> {
            BuildTools.rotateClipboard();
            rebuild();
        }));
        addRenderableWidget(PinButton.of(x + half + 4, y, half, 20, Component.literal("Flip"), () -> {
            BuildTools.flipClipboard();
            rebuild();
        }));
        y += ROW + 4;
        PinButton undo = PinButton.of(x, y, half, 20, Component.literal("Undo"), () -> {
            BuildTools.undo();
            rebuild();
        });
        undo.active = BuildTools.canUndo();
        addRenderableWidget(undo);
        PinButton redo = PinButton.of(x + half + 4, y, half, 20, Component.literal("Redo"), () -> {
            BuildTools.redo();
            rebuild();
        });
        redo.active = BuildTools.canRedo();
        addRenderableWidget(redo);
        y += ROW;
        addRenderableWidget(PinButton.of(x, y, columnWidth, 20,
                Component.literal(BuildTools.forceGhost() ? "Output: ghost guides" : "Output: real blocks"), () -> {
                    BuildTools.setForceGhost(!BuildTools.forceGhost());
                    rebuild();
                }));
        y += ROW;
        PinButton clearGhosts = PinButton.of(x, y, columnWidth, 20,
                Component.literal("Clear " + BuildTools.ghostCount() + " ghost blocks"), () -> {
                    BuildTools.clearGhosts();
                    rebuild();
                });
        clearGhosts.active = BuildTools.ghostCount() > 0;
        addRenderableWidget(clearGhosts);

        x = columns[2];
        y = top;
        addRenderableWidget(new AbstractSliderButton(x, y, columnWidth, 20, Component.empty(),
                (BuildTools.radius() - 1) / 31.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Radius: " + (1 + (int) Math.round(value * 31))));
            }

            @Override
            protected void applyValue() {
                BuildTools.setRadius(1 + (int) Math.round(value * 31));
            }
        });
        y += ROW + 4;
        action(x, y, half, "Sphere", () -> BuildTools.shape(BuildTools.Shape.SPHERE, false));
        action(x + half + 4, y, half, "Hollow sphere", () -> BuildTools.shape(BuildTools.Shape.SPHERE, true));
        y += ROW;
        action(x, y, half, "Dome", () -> BuildTools.shape(BuildTools.Shape.DOME, true));
        action(x + half + 4, y, half, "Cylinder", () -> BuildTools.shape(BuildTools.Shape.CYLINDER, false));
        y += ROW;
        action(x, y, half, "Cone", () -> BuildTools.shape(BuildTools.Shape.CONE, false));
        action(x + half + 4, y, half, "Tube", () -> BuildTools.shape(BuildTools.Shape.CYLINDER, true));
        y += ROW + 4;
        for (BuildTools.Tool value : BuildTools.Tool.values()) {
            addRenderableWidget(PinButton.of(x, y, columnWidth, 20, Component.literal(BuildTools.toolName(value)),
                    () -> {
                        BuildTools.setTool(value);
                        if (!BuildTools.isActive()) {
                            BuildTools.toggle();
                        }
                        onClose();
                    }).selected(BuildTools.tool() == value));
            y += ROW;
        }
    }

    private void action(int x, int y, int w, String label, Runnable run) {
        addRenderableWidget(PinButton.of(x, y, w, 20, Component.literal(label), () -> {
            onClose();
            run.run();
        }));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bottom = height - FOOTER_HEIGHT - 6;
        for (int column : columns) {
            renderPanel(guiGraphics, column - 6, CONTENT_TOP, column + columnWidth + 6, bottom);
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        String selection = BuildTools.hasSelection() ? BuildTools.volume() + " blocks selected" : "No selection";
        String[][] heads = {
                {"Selection", selection},
                {"Clipboard", BuildTools.clipboardSize() + " blocks copied"},
                {"Shapes & brushes", "Built where you look"}
        };
        for (int i = 0; i < 3; i++) {
            PinTheme.sectionHeader(guiGraphics, font, Component.literal(heads[i][0]), columns[i], CONTENT_TOP + 5);
            guiGraphics.text(font, Component.literal(heads[i][1]), columns[i], CONTENT_TOP + 18,
                    PinTheme.TEXT_MUTED, false);
        }
        String mode = switch (BuildTools.mode()) {
            case DIRECT -> "Singleplayer: edits go straight into the world.";
            case COMMANDS -> "This server lets you use /fill, so edits are sent as commands.";
            case GHOST -> "Ghost guides: edits show as outlines you build over by hand. Nothing is sent.";
        };
        guiGraphics.text(font, Component.literal(mode), columns[0], bottom - 22, PinTheme.TEXT_SOFT, false);
        guiGraphics.text(font, Component.literal("N build mode · left/right-click corners · held block = material, "
                        + "off-hand = what Replace swaps · Ctrl+C/V/Z/Y · [ ] radius"),
                columns[0], bottom - 11, PinTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
