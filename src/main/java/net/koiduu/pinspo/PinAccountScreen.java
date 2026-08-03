package net.koiduu.pinspo;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Account tab: log in either in the embedded browser, or in the player's own browser and paste the
 * resulting session back in.
 */
public class PinAccountScreen extends PinTabScreen {

    private static final String LOGIN_URL = "https://www.pinterest.com/login/";
    private static final int WIDGET_WIDTH = 220;

    @Nullable
    private EditBox sessionBox;
    @Nullable
    private Component feedback;
    private int panelTop;

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
        panelTop = CONTENT_TOP + 4;
        int y = panelTop + 40;
        boolean signedIn = PinterestAccount.isSignedIn();

        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.sign_in_external"),
                        button -> Util.getPlatform().openUri(LOGIN_URL))
                .bounds(x, y, WIDGET_WIDTH, 20)
                .build());
        y += 24;

        sessionBox = new EditBox(font, x, y, WIDGET_WIDTH - 62, 20,
                Component.translatable("screen.pinspo.session"));
        sessionBox.setHint(Component.translatable("screen.pinspo.session_hint"));
        sessionBox.setMaxLength(4096);
        addRenderableWidget(sessionBox);
        addRenderableWidget(Button
                .builder(Component.translatable("screen.pinspo.paste_sign_in"), button -> signInWithPastedSession())
                .bounds(x + WIDGET_WIDTH - 58, y, 58, 20)
                .build());
        y += 40;

        addRenderableWidget(Button
                .builder(Component.translatable(signedIn
                        ? "screen.pinspo.switch_account"
                        : "screen.pinspo.sign_in"), button -> signInWithEmbeddedBrowser(signedIn))
                .bounds(x, y, WIDGET_WIDTH, 20)
                .build());
        y += 24;

        Button signOut = Button
                .builder(Component.translatable("screen.pinspo.sign_out"), button -> {
                    PinterestAccount.signOut();
                    feedback = null;
                    rebuild();
                })
                .bounds(x, y, WIDGET_WIDTH, 20)
                .build();
        signOut.active = signedIn;
        addRenderableWidget(signOut);
    }

    private void signInWithPastedSession() {
        if (sessionBox == null || sessionBox.getValue().isBlank()) {
            return;
        }
        feedback = Component.translatable("screen.pinspo.signing_in");
        PinterestAccount.signInWithCookies(sessionBox.getValue())
                .whenComplete((name, throwable) -> minecraft.execute(() -> {
                    if (throwable != null || name == null || name.isEmpty()) {
                        feedback = Component.translatable("screen.pinspo.sign_in_failed");
                        return;
                    }
                    feedback = null;
                    if (sessionBox != null) {
                        sessionBox.setValue("");
                    }
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
        renderPanel(guiGraphics, left, panelTop, left + WIDGET_WIDTH + 24, panelTop + 152);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String username = PinterestAccount.username();
        Component status = PinterestAccount.isSignedIn()
                ? Component.translatable("screen.pinspo.signed_in",
                        username.isEmpty() ? Component.translatable("screen.pinspo.unknown_user") : username)
                : Component.translatable("screen.pinspo.signed_out");
        guiGraphics.drawCenteredString(font, status, width / 2, panelTop + 10, COLOR_TEXT);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.external_hint"),
                width / 2, panelTop + 24, COLOR_MUTED);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.sign_in_hint"),
                width / 2, panelTop + 116, COLOR_MUTED);
        if (feedback != null) {
            guiGraphics.drawCenteredString(font, feedback, width / 2, panelTop + 160, 0xFFE0A0A0);
        }
    }
}
