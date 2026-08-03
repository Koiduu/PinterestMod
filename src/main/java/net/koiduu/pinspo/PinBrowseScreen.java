package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Native Pinterest grid: searches through {@link PinterestApi} and draws the results as plain textures,
 * so browsing for a reference costs nothing like the embedded Chromium browser does.
 */
public class PinBrowseScreen extends PinTabScreen {

    private static final int HEADER_HEIGHT = 52;

    private final PinGrid grid = new PinGrid();

    @Nullable
    private EditBox searchBox;
    private String query = "";
    @Nullable
    private String bookmark;
    private boolean loading;
    private boolean exhausted;
    @Nullable
    private Component error;

    public PinBrowseScreen(@Nullable Screen parent) {
        this(parent, "");
    }

    /** Opens the search tab with {@code initialQuery} already typed in and searched. */
    public PinBrowseScreen(@Nullable Screen parent, String initialQuery) {
        super(Component.translatable("screen.pinspo.browse"), parent);
        this.query = initialQuery;
    }

    @Override
    protected Tab tab() {
        return Tab.SEARCH;
    }

    @Override
    protected void init() {
        addTabs();

        int searchWidth = Math.min(240, width - 150);
        searchBox = new EditBox(font, 10, 28, searchWidth, 18,
                Component.translatable("screen.pinspo.search"));
        searchBox.setHint(Component.translatable("screen.pinspo.search_hint"));
        searchBox.setMaxLength(120);
        searchBox.setValue(query);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.search_button"), button -> startSearch())
                .bounds(searchWidth + 16, 28, 60, 18)
                .build());
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.random"), button -> pinRandom())
                .bounds(searchWidth + 80, 28, 60, 18)
                .build());
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.full_browser"),
                        button -> minecraft.setScreen(new PinterestBrowserScreen(this)))
                .bounds(width - 74, 28, 64, 18)
                .build());

        grid.setBounds(10, HEADER_HEIGHT, width - 10, height - FOOTER_HEIGHT);
        if (!query.isEmpty() && grid.pins().isEmpty()) {
            loadMore();
        }
    }

    private void startSearch() {
        if (searchBox == null) {
            return;
        }
        String newQuery = searchBox.getValue().trim();
        if (newQuery.isEmpty()) {
            return;
        }
        query = newQuery;
        grid.clear();
        bookmark = null;
        exhausted = false;
        error = null;
        loadMore();
    }

    /** Pins a random result: from what is already loaded, or from a fresh search of the typed query. */
    private void pinRandom() {
        List<PinterestApi.Pin> loaded = grid.pins();
        if (!loaded.isEmpty()) {
            PinnedImage.pin(loaded.get(ThreadLocalRandom.current().nextInt(loaded.size())));
            onClose();
            return;
        }
        if (searchBox != null && !searchBox.getValue().isBlank()) {
            BuildBattleMode.pinRandom(searchBox.getValue().trim());
            onClose();
        }
    }

    private void loadMore() {
        if (loading || exhausted || query.isEmpty()) {
            return;
        }
        loading = true;
        String requested = query;
        PinterestApi.search(requested, bookmark).whenComplete((page, throwable) -> minecraft.execute(() -> {
            loading = false;
            if (!requested.equals(query)) {
                return;
            }
            if (throwable != null || page == null) {
                error = Component.translatable("screen.pinspo.search_failed");
                exhausted = true;
                PinSpoClient.LOGGER.warn("Pinterest search failed", throwable);
                return;
            }
            grid.addPins(page.pins());
            bookmark = page.bookmark();
            exhausted = bookmark == null || page.pins().isEmpty();
        }));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        grid.render(guiGraphics, font, mouseX, mouseY);

        if (grid.pins().isEmpty()) {
            Component message = error != null
                    ? error
                    : loading
                            ? Component.translatable("screen.pinspo.searching")
                            : Component.translatable("screen.pinspo.search_prompt");
            guiGraphics.drawCenteredString(font, message, width / 2, height / 2 - 4, 0xFFAAAAAA);
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.save_hint"),
                    10, HEADER_HEIGHT - 12, 0xFF808080, false);
            if (loading) {
                guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.searching"),
                        width / 2, height - FOOTER_HEIGHT - 12, 0xFFAAAAAA);
            }
        }

        if (!loading && !exhausted && grid.isNearEnd()) {
            loadMore();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        PinterestApi.Pin pin = grid.pinAt(event.x(), event.y());
        if (pin == null) {
            return false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            minecraft.setScreen(new FolderPickerScreen(this, pin));
            return true;
        }
        PinnedImage.pin(pin);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        grid.scrollBy(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && searchBox != null && searchBox.isFocused()) {
            startSearch();
            return true;
        }
        return super.keyPressed(event);
    }

}
