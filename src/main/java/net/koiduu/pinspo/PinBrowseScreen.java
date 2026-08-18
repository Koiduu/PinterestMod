package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
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

    private static final int SUGGESTION_COUNT = 6;

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
    /**
     * The home feed: instead of one search it walks a list of searches built from what the player keeps
     * picking, so scrolling feels like a Pinterest home page rather than a single result set.
     */
    private final boolean home;
    private List<String> feed = List.of();
    private int feedIndex;

    public PinBrowseScreen(@Nullable Screen parent) {
        this(parent, "");
    }

    /** Opens the search tab with {@code initialQuery} already typed in and searched. */
    public PinBrowseScreen(@Nullable Screen parent, String initialQuery) {
        this(parent, initialQuery, false);
    }

    /** The home feed: a mixed stream of pins from the player's own interests, with nothing typed in. */
    public static PinBrowseScreen home(@Nullable Screen parent) {
        return new PinBrowseScreen(parent, "", true);
    }

    private PinBrowseScreen(@Nullable Screen parent, String initialQuery, boolean home) {
        super(Component.translatable(home ? "screen.pinspo.home" : "screen.pinspo.browse"), parent);
        this.query = initialQuery;
        this.home = home;
    }

    @Override
    protected Tab tab() {
        return Tab.SEARCH;
    }

    @Override
    protected void init() {
        addTabs();

        int searchWidth = Math.max(80, Math.min(300, width - MARGIN * 2 - 140));
        searchBox = new EditBox(font, MARGIN, CONTENT_TOP, searchWidth, 20,
                Component.translatable("screen.pinspo.search"));
        searchBox.setHint(Component.translatable("screen.pinspo.search_hint"));
        searchBox.setMaxLength(120);
        // The feed's own searches are internal, so the box stays empty and ready for a real one.
        searchBox.setValue(home ? "" : query);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(PinButton.primary(MARGIN + searchWidth + 6, CONTENT_TOP, 60, 20,
                Component.translatable("screen.pinspo.search_button"), this::startSearch));
        addRenderableWidget(PinButton.of(MARGIN + searchWidth + 70, CONTENT_TOP, 60, 20,
                Component.translatable("screen.pinspo.random"), this::pinRandom));

        grid.setBounds(MARGIN, CONTENT_TOP + 38, width - MARGIN, height - FOOTER_HEIGHT - 8);
        if (home && feed.isEmpty()) {
            feed = PinTaste.feedQueries();
            query = feed.isEmpty() ? "" : feed.getFirst();
        }
        if (!query.isEmpty() && grid.pins().isEmpty()) {
            loadMore();
        }
        if (query.isEmpty()) {
            addSuggestions();
        }
    }

    /** One-click searches — the player's own interests first — centred under the prompt while empty. */
    private void addSuggestions() {
        List<String> suggestions = PinTaste.suggestions(SUGGESTION_COUNT);
        int rowWidth = 0;
        for (String suggestion : suggestions) {
            rowWidth += font.width(suggestion) + 16 + 6;
        }
        int x = Math.max(MARGIN, (width - (rowWidth - 6)) / 2);
        int y = height / 2 + 14;
        for (String suggestion : suggestions) {
            int buttonWidth = font.width(suggestion) + 16;
            if (x + buttonWidth > width - MARGIN) {
                x = Math.max(MARGIN, (width - (rowWidth - 6)) / 2);
                y += 24;
            }
            addRenderableWidget(PinButton.of(x, y, buttonWidth, 20, Component.literal(suggestion),
                    () -> search(suggestion)));
            x += buttonWidth + 6;
        }
    }

    private void search(String newQuery) {
        if (searchBox != null) {
            searchBox.setValue(newQuery);
        }
        startSearch();
    }

    private void startSearch() {
        if (searchBox == null) {
            return;
        }
        String newQuery = searchBox.getValue().trim();
        if (newQuery.isEmpty()) {
            return;
        }
        if (home) {
            // Searching leaves the feed rather than mixing its interests into the results.
            minecraft.setScreen(new PinBrowseScreen(parent, newQuery));
            return;
        }
        query = newQuery;
        grid.clear();
        bookmark = null;
        exhausted = false;
        error = null;
        // Rebuilding drops the suggestion chips and kicks off the first page.
        clearWidgets();
        init();
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
                PinSpoClient.LOGGER.warn("Pinterest search failed", throwable);
                if (home && advanceFeed()) {
                    return;
                }
                error = Component.translatable("screen.pinspo.search_failed");
                exhausted = true;
                return;
            }
            grid.addPins(page.pins());
            bookmark = page.bookmark();
            if (home) {
                // One page per interest keeps the feed mixed instead of turning into a single search.
                advanceFeed();
                return;
            }
            exhausted = bookmark == null || page.pins().isEmpty();
        }));
    }

    /** Points the feed at its next search; false once every search in it has been used. */
    private boolean advanceFeed() {
        if (feedIndex + 1 >= feed.size()) {
            exhausted = true;
            return false;
        }
        feedIndex++;
        query = feed.get(feedIndex);
        bookmark = null;
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        grid.render(guiGraphics, font, mouseX, mouseY);

        if (grid.pins().isEmpty()) {
            Component message = error != null
                    ? error
                    : loading
                            ? Component.translatable(home ? "screen.pinspo.loading_feed" : "screen.pinspo.searching")
                            : Component.translatable("screen.pinspo.search_prompt");
            guiGraphics.drawCenteredString(font, message, width / 2, height / 2 - 14, COLOR_MUTED);
            if (query.isEmpty()) {
                guiGraphics.drawCenteredString(font,
                        Component.translatable("screen.pinspo.suggestions"),
                        width / 2, height / 2 + 2, PinTheme.ACCENT);
            }
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.pinspo.save_hint"),
                    MARGIN, CONTENT_TOP + 24, COLOR_MUTED, false);
            if (loading) {
                guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.searching"),
                        width / 2, height - FOOTER_HEIGHT - 14, COLOR_MUTED);
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
            minecraft.setScreen(new PinActionScreen(this, pin));
            return true;
        }
        PinTaste.recordQuery(query);
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
