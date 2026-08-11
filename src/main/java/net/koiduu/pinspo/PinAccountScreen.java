package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Account tab, offering two ways in: email and password straight through the mod, or logging in with the
 * player's own browser and pasting the session back.
 */
public class PinAccountScreen extends PinTabScreen {

    private static final String LOGIN_URL = "https://www.pinterest.com/login/";
    private static final int WIDGET_WIDTH = 230;
    private static final int ROW = 22;
    private static final int SECTION_GAP = 16;
    private static final int HEADER_HEIGHT = 14;

    @Nullable
    private EditBox emailBox;
    @Nullable
    private EditBox passwordBox;
    @Nullable
    private EditBox sessionBox;
    @Nullable
    private Component feedback;
    private int panelTop;
    private int panelHeight;
    private int directHeaderY;
    private int browserHeaderY;
    private int stepsY;
    private int oneClickHeaderY;
    private int oneClickHintY;

    public PinAccountScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.pinspo.account"), parent);
    }

    @Override
    protected Tab tab() {
        return Tab.ACCOUNT;
    }

    @Override
    protected void init() {
        addTabs();

        int x = (width - WIDGET_WIDTH) / 2;
        panelTop = CONTENT_TOP;
        boolean signedIn = PinterestAccount.isSignedIn();
        // On short windows the sections sit closer together rather than running off the screen.
        int gap = height < 360 ? 6 : SECTION_GAP;

        // Every header claims its own row, so nothing is ever drawn on top of a widget.
        int y = panelTop + 8 + HEADER_HEIGHT;
        oneClickHeaderY = y;
        y += HEADER_HEIGHT;
        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.one_click"), this::startOneClickLogin));
        y += 20;
        oneClickHintY = y;
        y += HEADER_HEIGHT + gap;

        directHeaderY = y;
        y += HEADER_HEIGHT;

        emailBox = new EditBox(font, x, y, WIDGET_WIDTH, 18, Component.translatable("screen.pinspo.email"));
        emailBox.setHint(Component.translatable("screen.pinspo.email_hint"));
        emailBox.setMaxLength(128);
        addRenderableWidget(emailBox);
        y += ROW;

        passwordBox = new EditBox(font, x, y, WIDGET_WIDTH, 18, Component.translatable("screen.pinspo.password"));
        passwordBox.setHint(Component.translatable("screen.pinspo.password_hint"));
        passwordBox.setMaxLength(128);
        passwordBox.addFormatter((value, offset) ->
                Component.literal("*".repeat(value.length())).getVisualOrderText());
        addRenderableWidget(passwordBox);
        y += ROW;

        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_in_password"), this::signInWithPassword));
        y += 20 + gap;

        browserHeaderY = y;
        y += HEADER_HEIGHT;
        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_in_external"),
                () -> Util.getPlatform().openUri(LOGIN_URL)));
        y += ROW;

        sessionBox = new EditBox(font, x, y, WIDGET_WIDTH - 62, 18,
                Component.translatable("screen.pinspo.session"));
        sessionBox.setHint(Component.translatable("screen.pinspo.session_hint"));
        sessionBox.setMaxLength(4096);
        addRenderableWidget(sessionBox);
        addRenderableWidget(PinButton.of(x + WIDGET_WIDTH - 58, y, 58, 18,
                Component.translatable("screen.pinspo.paste"), this::pasteSession));
        y += ROW;

        stepsY = y;
        y += HEADER_HEIGHT + gap;

        PinButton signOut = PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_out"), () -> {
                    PinterestAccount.signOut();
                    feedback = null;
                    rebuild();
                });
        signOut.active = signedIn;
        addRenderableWidget(signOut);
        panelHeight = y + 20 + 10 - panelTop;
    }

    private void signInWithPassword() {
        if (emailBox == null || passwordBox == null
                || emailBox.getValue().isBlank() || passwordBox.getValue().isBlank()) {
            feedback = Component.translatable("screen.pinspo.need_credentials");
            return;
        }
        feedback = Component.translatable("screen.pinspo.signing_in");
        PinterestAccount.signInWithPassword(emailBox.getValue(), passwordBox.getValue())
                .whenComplete((result, throwable) -> minecraft.execute(() -> {
                    if (throwable != null || result == null) {
                        feedback = Component.translatable("screen.pinspo.password_failed");
                        return;
                    }
                    if (result.success()) {
                        if (passwordBox != null) {
                            passwordBox.setValue("");
                        }
                        feedback = null;
                        rebuild();
                        return;
                    }
                    feedback = failureReason(result.status());
                }));
    }

    /** Turns the HTTP status Pinterest answered with into something the player can act on. */
    private static Component failureReason(int status) {
        return switch (status) {
            case 429 -> Component.translatable("screen.pinspo.login_rate_limited");
            case 401, 403 -> Component.translatable("screen.pinspo.login_bot_check");
            case 0 -> Component.translatable("screen.pinspo.login_offline");
            default -> Component.translatable("screen.pinspo.password_failed");
        };
    }

    /**
     * Opens the local setup page that hands out the PinSpo bookmarklet, and signs in as soon as the
     * browser posts the session back.
     */
    private void startOneClickLogin() {
        String url = PinLoginServer.start(cookieHeader -> PinterestAccount.signInWithCookies(cookieHeader)
                .whenComplete((name, throwable) -> minecraft.execute(() -> {
                    PinLoginServer.stop();
                    if (throwable != null || name == null || name.isEmpty()) {
                        feedback = Component.translatable("screen.pinspo.sign_in_failed");
                        return;
                    }
                    feedback = null;
                    rebuild();
                })));
        if (url == null) {
            feedback = Component.translatable("screen.pinspo.helper_failed");
            return;
        }
        Util.getPlatform().openUri(url);
        feedback = Component.translatable("screen.pinspo.waiting_browser");
    }

    /** Fills the session field straight from the clipboard, so nothing has to be retyped. */
    private void pasteSession() {
        if (sessionBox == null) {
            return;
        }
        String clipboard = minecraft.keyboardHandler.getClipboard().trim();
        if (clipboard.isEmpty()) {
            feedback = Component.translatable("screen.pinspo.clipboard_empty");
            return;
        }
        sessionBox.setValue(clipboard);
        feedback = null;
        signInWithPastedSession();
    }

    private void signInWithPastedSession() {
        if (sessionBox == null || sessionBox.getValue().isBlank()) {
            feedback = Component.translatable("screen.pinspo.need_session");
            return;
        }
        feedback = Component.translatable("screen.pinspo.signing_in");
        PinterestAccount.signInWithCookies(sessionBox.getValue())
                .whenComplete((name, throwable) -> minecraft.execute(() -> {
                    if (throwable != null || name == null || name.isEmpty()) {
                        feedback = Component.translatable("screen.pinspo.sign_in_failed");
                        return;
                    }
                    if (sessionBox != null) {
                        sessionBox.setValue("");
                    }
                    feedback = null;
                    rebuild();
                }));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && sessionBox != null && sessionBox.isFocused()) {
            signInWithPastedSession();
            return true;
        }
        if (event.isConfirmation() && passwordBox != null && passwordBox.isFocused()) {
            signInWithPassword();
            return true;
        }
        return super.keyPressed(event);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WIDGET_WIDTH) / 2 - 12;
        PinTheme.panel(guiGraphics, left, panelTop, WIDGET_WIDTH + 24, panelHeight);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (width - WIDGET_WIDTH) / 2;
        String username = PinterestAccount.username();
        Component status = PinterestAccount.isSignedIn()
                ? Component.translatable("screen.pinspo.signed_in",
                        username.isEmpty() ? Component.translatable("screen.pinspo.unknown_user") : username)
                : Component.translatable("screen.pinspo.signed_out");
        guiGraphics.drawString(font, status, x, panelTop + 8, PinTheme.ACCENT, false);

        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.one_click"), x, oneClickHeaderY);
        guiGraphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable(PinLoginServer.isRunning()
                                ? "screen.pinspo.one_click_waiting"
                                : "screen.pinspo.one_click_hint").getString(),
                        WIDGET_WIDTH),
                x, oneClickHintY, PinTheme.TEXT_MUTED, false);

        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.direct"), x, directHeaderY);
        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.own_browser"), x, browserHeaderY);

        guiGraphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable("screen.pinspo.session_hint_line").getString(), WIDGET_WIDTH),
                x, stepsY, PinTheme.TEXT_MUTED, false);

        Component hint = feedback != null ? feedback : Component.translatable("screen.pinspo.account_hint");
        guiGraphics.drawString(font, font.plainSubstrByWidth(hint.getString(), width - MARGIN * 2),
                MARGIN, height - FOOTER_HEIGHT + 12,
                feedback != null ? PinTheme.ACCENT : PinTheme.TEXT_MUTED, false);
    }
}
