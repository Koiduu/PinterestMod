package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Account tab, offering three ways in: email and password straight through the mod, logging in with the
 * player's own browser and pasting the session back, or the embedded browser.
 */
public class PinAccountScreen extends PinTabScreen {

    private static final String LOGIN_URL = "https://www.pinterest.com/login/";
    private static final int WIDGET_WIDTH = 230;

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

        // Section 1: straight through the mod.
        int y = panelTop + 36;
        emailBox = new EditBox(font, x, y, WIDGET_WIDTH, 18, Component.translatable("screen.pinspo.email"));
        emailBox.setHint(Component.translatable("screen.pinspo.email_hint"));
        emailBox.setMaxLength(128);
        addRenderableWidget(emailBox);
        y += 22;

        passwordBox = new EditBox(font, x, y, WIDGET_WIDTH, 18, Component.translatable("screen.pinspo.password"));
        passwordBox.setHint(Component.translatable("screen.pinspo.password_hint"));
        passwordBox.setMaxLength(128);
        passwordBox.addFormatter((value, offset) ->
                Component.literal("*".repeat(value.length())).getVisualOrderText());
        addRenderableWidget(passwordBox);
        y += 22;

        addRenderableWidget(PinButton.primary(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_in_password"), this::signInWithPassword));

        // Section 2: own browser plus pasted session.
        y += 42;
        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_in_external"),
                () -> Util.getPlatform().openUri(LOGIN_URL)));
        y += 22;

        sessionBox = new EditBox(font, x, y, WIDGET_WIDTH - 62, 18,
                Component.translatable("screen.pinspo.session"));
        sessionBox.setHint(Component.translatable("screen.pinspo.session_hint"));
        sessionBox.setMaxLength(4096);
        addRenderableWidget(sessionBox);
        addRenderableWidget(PinButton.of(x + WIDGET_WIDTH - 58, y, 58, 18,
                Component.translatable("screen.pinspo.paste_sign_in"), this::signInWithPastedSession));

        // Section 3: embedded browser and sign out.
        y += 40;
        addRenderableWidget(PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable(signedIn ? "screen.pinspo.switch_account" : "screen.pinspo.sign_in"),
                () -> signInWithEmbeddedBrowser(signedIn)));
        y += 22;

        PinButton signOut = PinButton.of(x, y, WIDGET_WIDTH, 20,
                Component.translatable("screen.pinspo.sign_out"), () -> {
                    PinterestAccount.signOut();
                    feedback = null;
                    rebuild();
                });
        signOut.active = signedIn;
        addRenderableWidget(signOut);
        panelHeight = y + 20 + 12 - panelTop;
    }

    private void signInWithPassword() {
        if (emailBox == null || passwordBox == null
                || emailBox.getValue().isBlank() || passwordBox.getValue().isBlank()) {
            feedback = Component.translatable("screen.pinspo.need_credentials");
            return;
        }
        feedback = Component.translatable("screen.pinspo.signing_in");
        finish(PinterestAccount.signInWithPassword(emailBox.getValue(), passwordBox.getValue()),
                Component.translatable("screen.pinspo.password_failed"));
    }

    private void signInWithPastedSession() {
        if (sessionBox == null || sessionBox.getValue().isBlank()) {
            return;
        }
        feedback = Component.translatable("screen.pinspo.signing_in");
        finish(PinterestAccount.signInWithCookies(sessionBox.getValue()),
                Component.translatable("screen.pinspo.sign_in_failed"));
    }

    /** Clears the credential fields and rebuilds on success, or shows {@code failure}. */
    private void finish(CompletableFuture<String> attempt, Component failure) {
        attempt.whenComplete((name, throwable) -> minecraft.execute(() -> {
            if (throwable != null || name == null || name.isEmpty()) {
                feedback = failure;
                return;
            }
            feedback = null;
            rebuild();
        }));
    }

    private void signInWithEmbeddedBrowser(boolean signedIn) {
        if (signedIn) {
            PinterestAccount.signOut();
        }
        minecraft.setScreen(new PinterestBrowserScreen(new PinAccountScreen(parent), LOGIN_URL, true));
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
                Component.translatable("screen.pinspo.section.direct"), x, panelTop + 22);
        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.own_browser"), x, panelTop + 128);
        PinTheme.sectionHeader(guiGraphics, font,
                Component.translatable("screen.pinspo.section.embedded"), x, panelTop + 212);

        Component hint = feedback != null ? feedback : Component.translatable("screen.pinspo.account_hint");
        guiGraphics.drawString(font, font.plainSubstrByWidth(hint.getString(), width - MARGIN * 2),
                MARGIN, height - FOOTER_HEIGHT + 12,
                feedback != null ? 0xFFE0A0A0 : PinTheme.TEXT_MUTED, false);
    }
}
