package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Arranging camera windows: drag one to move it, drag its corner to resize it, click the cross to hide it.
 * The windows themselves are the HUD's, so what the player arranges is exactly what they will see.
 */
public class CameraLayoutScreen extends Screen {

    private static final int HANDLE = 8;

    @Nullable
    private final Screen parent;
    @Nullable
    private CameraPoints.Point dragging;
    private boolean resizing;
    private double grabX;
    private double grabY;

    public CameraLayoutScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.camera_layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(PinButton.primary(width / 2 - 40, height - 28, 80, 20,
                Component.translatable("gui.done"), this::onClose));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        List<CameraPoints.Point> points = CameraPoints.visibleHere();
        Component hint = points.isEmpty()
                ? Component.translatable("screen.pinspo.camera_layout_empty", PinSpoClient.CAMERA_KEY.getTranslatedKeyMessage())
                : Component.translatable("screen.pinspo.camera_layout_hint");
        int hintWidth = font.width(hint) + 16;
        PinTheme.panel(guiGraphics, width / 2 - hintWidth / 2, 8, hintWidth, 18);
        guiGraphics.centeredText(font, hint, width / 2, 13, PinTheme.TEXT_SOFT);
        for (CameraPoints.Point point : points) {
            CameraPoints.Box box = CameraPoints.box(point, width, height);
            boolean active = point == dragging || box.contains(mouseX, mouseY);
            PinTheme.roundedOutline(guiGraphics, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2,
                    active ? PinTheme.ACCENT : PinTheme.BORDER_BRIGHT);
            // Resize grip in the bottom-right corner.
            int right = box.x() + box.width();
            int bottom = box.y() + box.height();
            guiGraphics.fill(right - HANDLE, bottom - 2, right, bottom, PinTheme.ACCENT);
            guiGraphics.fill(right - 2, bottom - HANDLE, right, bottom, PinTheme.ACCENT);
            // Close cross in the title strip.
            boolean overClose = overClose(box, mouseX, mouseY);
            if (active) {
                Component fov = Component.translatable("screen.pinspo.camera_fov", point.fov > 0.0F
                        ? Math.round(point.fov) + "\u00b0"
                        : Component.translatable("screen.pinspo.camera_fov_game").getString());
                int fovWidth = font.width(fov) + 8;
                guiGraphics.fill(box.x(), bottom - 12, box.x() + fovWidth, bottom, 0xC0000000);
                guiGraphics.text(font, fov, box.x() + 4, bottom - 10, PinTheme.TEXT_SOFT, false);
            }
            guiGraphics.text(font, "\u00d7", right - 8, box.y() + 1, overClose ? PinTheme.ACCENT : PinTheme.TEXT_MUTED, false);
        }
    }

    private static boolean overClose(CameraPoints.Box box, double mouseX, double mouseY) {
        int right = box.x() + box.width();
        return mouseX >= right - 10 && mouseX < right && mouseY >= box.y() && mouseY < box.viewTop();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        List<CameraPoints.Point> points = CameraPoints.visibleHere();
        // Topmost (last drawn) window wins.
        for (int i = points.size() - 1; i >= 0; i--) {
            CameraPoints.Point point = points.get(i);
            CameraPoints.Box box = CameraPoints.box(point, width, height);
            if (!box.contains(event.x(), event.y())) {
                continue;
            }
            if (overClose(box, event.x(), event.y())) {
                CameraPoints.toggle(point);
                return true;
            }
            if (event.button() == 1) {
                point.fov = 0.0F;
                CameraPoints.save();
                return true;
            }
            dragging = point;
            resizing = event.x() >= box.x() + box.width() - HANDLE * 1.5 && event.y() >= box.y() + box.height() - HANDLE * 1.5;
            grabX = event.x() - box.x();
            grabY = event.y() - box.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<CameraPoints.Point> points = CameraPoints.visibleHere();
        for (int i = points.size() - 1; i >= 0; i--) {
            CameraPoints.Point point = points.get(i);
            if (CameraPoints.box(point, width, height).contains(mouseX, mouseY)) {
                CameraPoints.zoom(point, verticalAmount);
                CameraPoints.save();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        CameraPoints.Box box = CameraPoints.box(dragging, width, height);
        if (resizing) {
            dragging.windowWidth = Math.clamp((float) ((event.x() - box.x()) / width),
                    CameraPoints.MIN_WINDOW, CameraPoints.MAX_WINDOW);
        } else {
            dragging.windowX = Math.clamp((float) ((event.x() - grabX) / width), 0.0F, 1.0F);
            dragging.windowY = Math.clamp((float) ((event.y() - grabY) / height), 0.0F, 1.0F);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            CameraPoints.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        CameraPoints.save();
        minecraft.setScreenAndShow(parent);
    }
}
