package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The building tools in one place: the wall photo, camera points and the colour picker. Each card says in
 * one line what it does and which key starts it, so nothing here needs a manual.
 */
public class PinBuildScreen extends PinTabScreen {

    private static final int ROW = 24;

    private int columnWidth;
    private int leftX;
    private int rightX;
    private int top;
    private int colourTop;
    @Nullable
    private Component feedback;

    public PinBuildScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.build"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.BUILD;
    }

    @Override
    protected void init() {
        addTabs();
        columnWidth = Math.max(120, Math.min(230, (width - MARGIN * 2 - 32) / 2));
        leftX = MARGIN + 8;
        rightX = width - MARGIN - 8 - columnWidth;
        top = CONTENT_TOP + 6;

        // Wall photo
        int y = top + 38;
        WallPhotos.Photo photo = WallPhotos.mine();
        int half = (columnWidth - 4) / 2;
        if (photo == null) {
            PinButton place = PinButton.primary(leftX, y, columnWidth, 20,
                    Component.translatable("screen.pinspo.wall_place"), () -> {
                        WallPhotos.placeAtCrosshair(minecraft);
                        rebuild();
                    });
            place.active = !PinnedImage.currentUrl().isEmpty();
            addRenderableWidget(place);
        } else {
            addRenderableWidget(PinButton.primary(leftX, y, half, 20,
                    Component.translatable("screen.pinspo.wall_edit_button"),
                    () -> minecraft.setScreenAndShow(new WallEditScreen(this))));
            addRenderableWidget(PinButton.of(leftX + half + 4, y, half, 20, Component.translatable(photo.shared
                    ? "option.pinspo.wall_shared.on" : "option.pinspo.wall_shared.off"), () -> {
                String problem = WallPhotos.setShared(!photo.shared);
                feedback = problem == null ? null : Component.translatable(problem);
                rebuild();
            }));
            y += ROW;
            addRenderableWidget(PinButton.of(leftX, y, half, 20, Component.translatable("screen.pinspo.wall_here"), () -> {
                WallPhotos.placeAtCrosshair(minecraft);
                rebuild();
            }));
            addRenderableWidget(PinButton.of(leftX + half + 4, y, half, 20,
                    Component.translatable("option.pinspo.wall_remove"), () -> {
                        WallPhotos.remove();
                        rebuild();
                    }));
        }

        // Colour picker
        colourTop = y + ROW + 18;
        int colourY = colourTop + 38;
        PinButton fromPin = PinButton.primary(leftX, colourY, half, 20,
                Component.translatable("screen.pinspo.colour_use_pinned"),
                () -> minecraft.setScreenAndShow(new ColourPickScreen(parent, PinnedImage.currentUrl())));
        fromPin.active = !PinnedImage.currentUrl().isEmpty();
        addRenderableWidget(fromPin);
        addRenderableWidget(PinButton.of(leftX + half + 4, colourY, half, 20,
                Component.translatable("screen.pinspo.colour_other"),
                () -> minecraft.setScreenAndShow(new ColourPickScreen(parent, null))));

        // Cameras
        int cameraY = top + 38;
        List<CameraPoints.Point> cameras = CameraPoints.here();
        int small = 44;
        for (CameraPoints.Point point : cameras) {
            addRenderableWidget(PinButton.of(rightX + columnWidth - small * 2 - 4, cameraY, small, 20,
                    Component.translatable(point.visible ? "screen.pinspo.camera_hide" : "screen.pinspo.camera_show"), () -> {
                        CameraPoints.toggle(point);
                        rebuild();
                    }));
            addRenderableWidget(PinButton.of(rightX + columnWidth - small, cameraY, small, 20,
                    Component.translatable("screen.pinspo.camera_delete"), () -> {
                        CameraPoints.remove(point);
                        rebuild();
                    }));
            cameraY += ROW;
        }
        if (cameras.size() < CameraPoints.MAX_PER_WORLD) {
            addRenderableWidget(PinButton.of(rightX, cameraY, half, 20,
                    Component.translatable("screen.pinspo.camera_plant_here"), () -> {
                        CameraPoints.plant(minecraft);
                        rebuild();
                    }));
        }
        PinButton arrange = PinButton.primary(rightX + half + 4, cameraY, half, 20,
                Component.translatable("screen.pinspo.camera_arrange"),
                () -> minecraft.setScreenAndShow(new CameraLayoutScreen(this)));
        arrange.active = !CameraPoints.visibleHere().isEmpty();
        addRenderableWidget(arrange);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bottom = height - FOOTER_HEIGHT - 6;
        renderPanel(guiGraphics, MARGIN, CONTENT_TOP, leftX + columnWidth + 8, colourTop - 8);
        renderPanel(guiGraphics, MARGIN, colourTop - 4, leftX + columnWidth + 8, bottom);
        renderPanel(guiGraphics, rightX - 8, CONTENT_TOP, width - MARGIN, bottom);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        WallPhotos.Photo photo = WallPhotos.mine();
        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.wall"), leftX, top);
        Component wallLine = photo == null
                ? (PinnedImage.currentUrl().isEmpty()
                        ? Component.translatable("screen.pinspo.wall_need_pin")
                        : Component.translatable("screen.pinspo.wall_how", PinSpoClient.WALL_KEY.getTranslatedKeyMessage()))
                : Component.translatable(photo.shared ? "screen.pinspo.wall_status_shared" : "screen.pinspo.wall_status",
                        WallEditScreen.blocks(photo.size), Math.round(photo.opacity * 100.0F));
        wrapped(guiGraphics, wallLine, leftX, top + 15);

        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.colour"), leftX, colourTop);
        wrapped(guiGraphics, Component.translatable("screen.pinspo.colour_how"), leftX, colourTop + 15);

        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.section.cameras"), rightX, top);
        wrapped(guiGraphics, Component.translatable("screen.pinspo.camera_how",
                PinSpoClient.CAMERA_KEY.getTranslatedKeyMessage()), rightX, top + 15);
        int cameraY = top + 38;
        for (CameraPoints.Point point : CameraPoints.here()) {
            guiGraphics.fill(rightX, cameraY + 8, rightX + 4, cameraY + 12, point.visible ? PinTheme.ACCENT : PinTheme.BORDER);
            guiGraphics.text(font, point.name, rightX + 8, cameraY + 6, point.visible ? PinTheme.TEXT_SOFT : COLOR_MUTED, false);
            cameraY += ROW;
        }
        if (feedback != null) {
            guiGraphics.text(font, feedback, MARGIN, height - FOOTER_HEIGHT + 12, PinTheme.ACCENT, false);
        }
    }

    /** Two muted lines at most: the cards explain themselves in a sentence, not a paragraph. */
    private void wrapped(GuiGraphicsExtractor guiGraphics, Component text, int x, int y) {
        var lines = font.split(text, columnWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            guiGraphics.text(font, lines.get(i), x, y + i * 10, COLOR_MUTED, false);
        }
    }
}
