package net.koiduu.pinspo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Account tab: log in on pinterest.com in the embedded browser, then switch or sign out from here. */
public class PinAccountScreen extends PinTabScreen {

    private static final String LOGIN_URL = "https://www.pinterest.com/login/";
    private static final int WIDGET_WIDTH = 200;

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
        int y = Math.max(60, height / 2 - 40);
        boolean signedIn = PinterestAccount.isSignedIn();

        addRenderableWidget(Button
                .builder(Component.translatable(signedIn
                        ? "screen.pinspo.switch_account"
                        : "screen.pinspo.sign_in"), button -> signIn(signedIn))
                .bounds(x, y, WIDGET_WIDTH, 20)
                .build());
        y += 24;

        Button signOut = Button
                .builder(Component.translatable("screen.pinspo.sign_out"), button -> {
                    PinterestAccount.signOut();
                    rebuild();
                })
                .bounds(x, y, WIDGET_WIDTH, 20)
                .build();
        signOut.active = signedIn;
        addRenderableWidget(signOut);
    }

    private void signIn(boolean signedIn) {
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        String username = PinterestAccount.username();
        Component status = PinterestAccount.isSignedIn()
                ? Component.translatable("screen.pinspo.signed_in",
                        username.isEmpty() ? Component.translatable("screen.pinspo.unknown_user") : username)
                : Component.translatable("screen.pinspo.signed_out");
        guiGraphics.drawCenteredString(font, status, width / 2, Math.max(36, height / 2 - 70), 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.pinspo.sign_in_hint"),
                width / 2, Math.max(50, height / 2 - 56), 0xFF909090);
    }
}
