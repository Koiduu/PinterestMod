package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Composition guides drawn over the pinned reference: the same overlays photographers use, so
 * proportions can be copied into a build without eyeballing them.
 */
public final class PinGuide {

    /** Lines are hairlines, so a plain white with a low alpha reads on both dark and bright images. */
    private static final int LINE = 0xFFFFFF;
    private static final int LINE_ALPHA = 150;
    /** Spiral arcs are approximated with this many segments per quarter turn. */
    private static final int ARC_STEPS = 24;
    private static final int SPIRAL_TURNS = 8;

    public enum Guide {
        OFF,
        THIRDS,
        GOLDEN,
        SPIRAL,
        DIAGONALS,
        CENTRE,
        QUARTERS,
        GRID;

        public Guide next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** The floor guides skip the spiral, which has no sensible layout on a square plot. */
        public Guide nextFloor() {
            Guide next = next();
            return next == SPIRAL ? next.next() : next;
        }

        public Component label() {
            return Component.translatable("guide.pinspo." + name().toLowerCase(Locale.ROOT));
        }
    }

    private PinGuide() {
    }

    /** Draws the guide inside the rectangle the reference was blitted into. */
    public static void render(GuiGraphicsExtractor guiGraphics, Guide guide, int x, int y, int width, int height,
                              int overlayAlpha) {
        if (guide == Guide.OFF || width < 8 || height < 8) {
            return;
        }
        int colour = Math.min(LINE_ALPHA, overlayAlpha) << 24 | LINE;
        switch (guide) {
            case THIRDS -> lattice(guiGraphics, x, y, width, height, colour, 1.0F / 3.0F, 2.0F / 3.0F);
            case GOLDEN -> lattice(guiGraphics, x, y, width, height, colour, 0.382F, 0.618F);
            case QUARTERS -> lattice(guiGraphics, x, y, width, height, colour, 0.25F, 0.5F, 0.75F);
            case CENTRE -> lattice(guiGraphics, x, y, width, height, colour, 0.5F);
            case GRID -> lattice(guiGraphics, x, y, width, height, colour,
                    0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F);
            case DIAGONALS -> diagonals(guiGraphics, x, y, width, height, colour);
            case SPIRAL -> spiral(guiGraphics, x, y, width, height, colour);
            case OFF -> {
            }
        }
        outline(guiGraphics, x, y, width, height, colour);
    }

    /** Horizontal and vertical lines at the given fractions of the frame. */
    private static void lattice(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int colour,
                                float... fractions) {
        for (float fraction : fractions) {
            int lineX = x + Math.round(width * fraction);
            int lineY = y + Math.round(height * fraction);
            guiGraphics.fill(lineX, y, lineX + 1, y + height, colour);
            guiGraphics.fill(x, lineY, x + width, lineY + 1, colour);
        }
    }

    /** Both diagonals plus the reciprocals dropped from the opposite corners. */
    private static void diagonals(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int colour) {
        int right = x + width;
        int bottom = y + height;
        line(guiGraphics, x, y, right, bottom, colour);
        line(guiGraphics, right, y, x, bottom, colour);
        // Reciprocals: the perpendicular from each remaining corner onto the main diagonal.
        float lengthSquared = (float) width * width + (float) height * height;
        float alongX = width * ((float) width * width) / lengthSquared;
        float alongY = height * ((float) width * width) / lengthSquared;
        line(guiGraphics, right, y, x + Math.round(alongX), y + Math.round(alongY), colour);
        line(guiGraphics, x, bottom, right - Math.round(alongX), bottom - Math.round(alongY), colour);
    }

    /**
     * Fibonacci spiral: repeatedly cut the largest square off the frame and draw the quarter circle
     * inside it, rotating a quarter turn each time.
     */
    private static void spiral(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int colour) {
        float left = x;
        float top = y;
        float right = x + width;
        float bottom = y + height;
        for (int turn = 0; turn < SPIRAL_TURNS; turn++) {
            float boxWidth = right - left;
            float boxHeight = bottom - top;
            if (boxWidth < 3.0F || boxHeight < 3.0F) {
                return;
            }
            switch (turn % 4) {
                case 0 -> {
                    arc(guiGraphics, left + boxHeight, top, boxHeight, 180.0F, 90.0F, colour);
                    left += boxHeight;
                }
                case 1 -> {
                    arc(guiGraphics, right, bottom, boxWidth, 180.0F, 270.0F, colour);
                    bottom -= boxWidth;
                }
                case 2 -> {
                    arc(guiGraphics, right - boxHeight, bottom, boxHeight, 360.0F, 270.0F, colour);
                    right -= boxHeight;
                }
                default -> {
                    arc(guiGraphics, left, top, boxWidth, 0.0F, 90.0F, colour);
                    top += boxWidth;
                }
            }
        }
    }

    private static void arc(GuiGraphicsExtractor guiGraphics, float centreX, float centreY, float radius,
                           float fromDegrees, float toDegrees, int colour) {
        int previousX = Integer.MIN_VALUE;
        int previousY = Integer.MIN_VALUE;
        for (int step = 0; step <= ARC_STEPS; step++) {
            double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * step / (float) ARC_STEPS);
            int pointX = Math.round(centreX + (float) (radius * Math.cos(angle)));
            int pointY = Math.round(centreY + (float) (radius * Math.sin(angle)));
            if (previousX != Integer.MIN_VALUE) {
                line(guiGraphics, previousX, previousY, pointX, pointY, colour);
            }
            previousX = pointX;
            previousY = pointY;
        }
    }

    /** Minecraft can only fill rectangles, so slanted lines are stepped a pixel at a time. */
    private static void line(GuiGraphicsExtractor guiGraphics, int fromX, int fromY, int toX, int toY, int colour) {
        int deltaX = Math.abs(toX - fromX);
        int deltaY = Math.abs(toY - fromY);
        int stepX = fromX < toX ? 1 : -1;
        int stepY = fromY < toY ? 1 : -1;
        int error = deltaX - deltaY;
        int pointX = fromX;
        int pointY = fromY;
        while (true) {
            guiGraphics.fill(pointX, pointY, pointX + 1, pointY + 1, colour);
            if (pointX == toX && pointY == toY) {
                return;
            }
            int doubled = error * 2;
            if (doubled > -deltaY) {
                error -= deltaY;
                pointX += stepX;
            }
            if (doubled < deltaX) {
                error += deltaX;
                pointY += stepY;
            }
        }
    }

    private static void outline(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int colour) {
        guiGraphics.fill(x, y, x + width, y + 1, colour);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, colour);
        guiGraphics.fill(x, y, x + 1, y + height, colour);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, colour);
    }
}
