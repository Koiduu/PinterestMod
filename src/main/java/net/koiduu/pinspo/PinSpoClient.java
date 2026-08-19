package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PinSpoClient implements ClientModInitializer {

    public static final String MOD_ID = "pinspo";
    public static final Logger LOGGER = LoggerFactory.getLogger("PinSpo");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "pinspo"));

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.pinspo.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(OPEN_KEY);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "pinned_image"),
                (guiGraphics, deltaTracker) -> {
                    PinnedImage.render(guiGraphics);
                    PinNotifications.render(guiGraphics, guiGraphics.guiWidth());
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(PinSpoClient::onEndTick);
        PinnedImage.restore();

        // Hypixel announces themes as system messages, but relayed player chat arrives on CHAT. Both are
        // read on the ALLOW events, because a PinSpo request or reference is swallowed rather than shown:
        // it is the mod talking to itself and has no business in the player's chat box.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                return true;
            }
            BuildBattleMode.onChatMessage(message);
            return handle(message);
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            BuildBattleMode.onChatMessage(message);
            return handle(message);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BuildBattleMode.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PinnedImage.clear());
    }

    /** Files a chat line into PinSpo and says whether the player should still see it. */
    private static boolean handle(Component message) {
        PinChat.onChatMessage(message);
        return !PinChat.isProtocolLine(message);
    }

    private static void onEndTick(Minecraft client) {
        while (OPEN_KEY.consumeClick()) {
            if (client.screen != null) {
                continue;
            }
            if (PinnedImage.isPinned()) {
                client.setScreen(new PinSettingsScreen(null));
            } else {
                client.setScreen(new PinBrowseScreen(null));
            }
        }
    }
}
