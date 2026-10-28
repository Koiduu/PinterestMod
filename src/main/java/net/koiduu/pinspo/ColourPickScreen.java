package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Point at any pixel of a photo and see its exact colour and the blocks that come closest to it. Hovering
 * previews; clicking locks the pick so the list can be read without holding the mouse still.
 */
public class ColourPickScreen extends PinTabScreen {

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(PinSpoClient.MOD_ID, "colour_pick");
    private static final int SIDE_WIDTH = 150;
    private static final int LOUPE_CELLS = 9;
    private static final int LOUPE_CELL = 6;
    private static final int MATCHES = 6;

    @Nullable
    private DynamicTexture texture;
    private int imageWidth;
    private int imageHeight;
    private boolean loading;
    @Nullable
    private Component feedback;

    private int pickX = -1;
    private int pickY = -1;
    private boolean locked;
    private List<BlockPalette.Match> matches = List.of();
    private int matchesFor = -1;

    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;

    @Nullable
    private String pendingUrl;

    public ColourPickScreen(@Nullable Screen parent, @Nullable String url) {
        super(Component.translatable("screen.pinspo.colour_pick"), parent);
        this.pendingUrl = url;
    }

    @Override
    protected Tab tab() {
        return Tab.BUILD;
    }

    @Override
    protected void init() {
        addTabs();
        BlockPalette.prepare();
        int sideX = width - MARGIN - SIDE_WIDTH;
        int buttonY = height - FOOTER_HEIGHT + 6;
        addRenderableWidget(PinButton.of(MARGIN, buttonY, 96, 20,
                Component.translatable("screen.pinspo.choose_file"), this::chooseFile));
        PinButton pinned = PinButton.of(MARGIN + 100, buttonY, 96, 20,
                Component.translatable("screen.pinspo.colour_use_pinned"), () -> load(PinnedImage.currentUrl()));
        pinned.active = !PinnedImage.currentUrl().isEmpty();
        addRenderableWidget(pinned);
        addRenderableWidget(PinButton.of(sideX, buttonY, 70, 20,
                Component.translatable("screen.pinspo.colour_copy"), this::copyHex));
        if (pendingUrl != null) {
            String url = pendingUrl;
            pendingUrl = null;
            load(url);
        }
    }

    private void load(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        loading = true;
        feedback = null;
        CompletableFuture.supplyAsync(() -> PinnedImage.loadFullImage(url)).whenComplete((image, error) ->
                minecraft.execute(() -> {
                    loading = false;
                    if (image == null) {
                        feedback = Component.translatable("screen.pinspo.colour_failed");
                        return;
                    }
                    show(image);
                }));
    }

    private void show(NativeImage image) {
        release();
        texture = new DynamicTexture(() -> "PinSpo colour picker", image);
        minecraft.getTextureManager().register(TEXTURE_ID, texture);
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
        pickX = imageWidth / 2;
        pickY = imageHeight / 2;
        locked = false;
        matchesFor = -1;
        PinSpoClient.LOGGER.info("Colour picker opened a {}x{} image", imageWidth, imageHeight);
    }

    private void chooseFile() {
        feedback = Component.translatable("screen.pinspo.choosing_file");
        FileChooser.open(chosen -> {
                feedback = null;
                if (chosen == null) {
                    return;
                }
                PinterestApi.Pin pin = LocalImages.importFile(Path.of(chosen));
                if (pin == null) {
                    feedback = Component.translatable("screen.pinspo.bad_file");
                    return;
                }
                load(pin.imageUrl());
        });
    }

    private void copyHex() {
        int rgb = pickedColour();
        if (rgb >= 0) {
            minecraft.keyboardHandler.setClipboard(hex(rgb));
            feedback = Component.translatable("screen.pinspo.colour_copied", hex(rgb));
        }
    }

    private int pickedColour() {
        if (texture == null || texture.getPixels() == null || pickX < 0 || pickY < 0) {
            return -1;
        }
        return texture.getPixels().getPixel(pickX, pickY) & 0xFFFFFF;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private void layoutImage() {
        int areaX = MARGIN;
        int areaY = CONTENT_TOP;
        int areaWidth = width - MARGIN * 3 - SIDE_WIDTH;
        int areaHeight = height - FOOTER_HEIGHT - 8 - areaY;
        if (imageWidth <= 0 || imageHeight <= 0) {
            boxX = areaX;
            boxY = areaY;
            boxWidth = areaWidth;
            boxHeight = areaHeight;
            return;
        }
        float fit = Math.min((float) areaWidth / imageWidth, (float) areaHeight / imageHeight);
        boxWidth = Math.max(1, Math.round(imageWidth * fit));
        boxHeight = Math.max(1, Math.round(imageHeight * fit));
        boxX = areaX + (areaWidth - boxWidth) / 2;
        boxY = areaY + (areaHeight - boxHeight) / 2;
    }

    private boolean overImage(double mouseX, double mouseY) {
        return texture != null && mouseX >= boxX && mouseX < boxX + boxWidth && mouseY >= boxY && mouseY < boxY + boxHeight;
    }

    private void pickAt(double mouseX, double mouseY) {
        pickX = Math.clamp((int) ((mouseX - boxX) * imageWidth / boxWidth), 0, imageWidth - 1);
        pickY = Math.clamp((int) ((mouseY - boxY) * imageHeight / boxHeight), 0, imageHeight - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (overImage(event.x(), event.y())) {
            pickAt(event.x(), event.y());
            locked = true;
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        layoutImage();
        if (!locked && overImage(mouseX, mouseY)) {
            pickAt(mouseX, mouseY);
        }
        int sideX = width - MARGIN - SIDE_WIDTH;
        int bottom = height - FOOTER_HEIGHT - 6;
        renderPanel(guiGraphics, sideX - 6, CONTENT_TOP - 4, width - MARGIN + 4, bottom);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        if (texture == null) {
            Component message = loading ? Component.translatable("screen.pinspo.colour_loading")
                    : Component.translatable("screen.pinspo.colour_empty");
            guiGraphics.centeredText(font, message, boxX + boxWidth / 2, boxY + boxHeight / 2 - 4, COLOR_MUTED);
        } else {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, boxX, boxY, 0.0F, 0.0F,
                    boxWidth, boxHeight, boxWidth, boxHeight);
            int markX = boxX + (int) ((pickX + 0.5F) * boxWidth / imageWidth);
            int markY = boxY + (int) ((pickY + 0.5F) * boxHeight / imageHeight);
            guiGraphics.fill(markX - 5, markY, markX - 1, markY + 1, 0xFFFFFFFF);
            guiGraphics.fill(markX + 2, markY, markX + 6, markY + 1, 0xFFFFFFFF);
            guiGraphics.fill(markX, markY - 5, markX + 1, markY - 1, 0xFFFFFFFF);
            guiGraphics.fill(markX, markY + 2, markX + 1, markY + 6, 0xFFFFFFFF);
        }
        renderSide(guiGraphics, sideX);
        if (feedback != null) {
            guiGraphics.text(font, feedback, MARGIN + 204, height - FOOTER_HEIGHT + 12, PinTheme.ACCENT, false);
        }
    }

    private void renderSide(GuiGraphicsExtractor guiGraphics, int x) {
        int y = CONTENT_TOP + 2;
        PinTheme.sectionHeader(guiGraphics, font, Component.translatable(locked
                ? "screen.pinspo.colour_locked" : "screen.pinspo.colour_hover"), x, y);
        y += 16;
        int rgb = pickedColour();
        if (rgb < 0) {
            guiGraphics.text(font, Component.translatable("screen.pinspo.colour_hint"), x, y, COLOR_MUTED, false);
            return;
        }
        // Loupe: the pixels around the pick, enlarged, with the picked one outlined.
        NativeImage pixels = texture.getPixels();
        int half = LOUPE_CELLS / 2;
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                int px = Math.clamp(pickX + dx, 0, imageWidth - 1);
                int py = Math.clamp(pickY + dy, 0, imageHeight - 1);
                int cx = x + (dx + half) * LOUPE_CELL;
                int cy = y + (dy + half) * LOUPE_CELL;
                guiGraphics.fill(cx, cy, cx + LOUPE_CELL, cy + LOUPE_CELL, 0xFF000000 | pixels.getPixel(px, py));
            }
        }
        int loupe = LOUPE_CELLS * LOUPE_CELL;
        guiGraphics.outline(x + half * LOUPE_CELL - 1, y + half * LOUPE_CELL - 1, LOUPE_CELL + 2, LOUPE_CELL + 2, 0xFFFFFFFF);
        guiGraphics.outline(x - 1, y - 1, loupe + 2, loupe + 2, PinTheme.BORDER);

        int infoX = x + loupe + 8;
        PinTheme.roundedRect(guiGraphics, infoX, y, 30, 18, 0xFF000000 | rgb);
        guiGraphics.text(font, hex(rgb), infoX, y + 24, PinTheme.TEXT, false);
        guiGraphics.text(font, (rgb >> 16 & 0xFF) + " " + (rgb >> 8 & 0xFF) + " " + (rgb & 0xFF),
                infoX, y + 35, COLOR_MUTED, false);
        guiGraphics.text(font, (pickX + 1) + ", " + (pickY + 1), infoX, y + 46, PinTheme.TEXT_DISABLED, false);

        y += loupe + 10;
        PinTheme.sectionHeader(guiGraphics, font, Component.translatable("screen.pinspo.colour_blocks"), x, y);
        y += 16;
        if (matchesFor != rgb || matches.isEmpty()) {
            matches = BlockPalette.nearest(rgb, MATCHES);
            matchesFor = matches.isEmpty() ? -1 : rgb;
        }
        if (matches.isEmpty()) {
            guiGraphics.text(font, Component.translatable("screen.pinspo.colour_palette_loading"), x, y, COLOR_MUTED, false);
            return;
        }
        int bottom = height - FOOTER_HEIGHT - 10;
        for (BlockPalette.Match match : matches) {
            if (y + 20 > bottom) {
                break;
            }
            guiGraphics.item(new ItemStack(match.block()), x, y + 1);
            guiGraphics.fill(x + 20, y + 3, x + 24, y + 15, 0xFF000000 | match.rgb());
            guiGraphics.text(font, font.plainSubstrByWidth(match.block().getName().getString(), SIDE_WIDTH - 58),
                    x + 28, y + 5, PinTheme.TEXT_SOFT, false);
            String score = match.similarity() + "%";
            guiGraphics.text(font, score, x + SIDE_WIDTH - font.width(score), y + 5,
                    match == matches.getFirst() ? PinTheme.ACCENT : COLOR_MUTED, false);
            y += 20;
        }
    }

    private void release() {
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
    }

    @Override
    public void removed() {
        release();
        super.removed();
    }
}
