package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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

    /** Cycles the composition guide drawn over the reference. */
    public static final KeyMapping GUIDE_KEY = new KeyMapping(
            "key.pinspo.guide",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Cycles the composition guide drawn on the build plot's floor. */
    public static final KeyMapping PLOT_KEY = new KeyMapping(
            "key.pinspo.plot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN_KEY);
        KeyMappingHelper.registerKeyMapping(GUIDE_KEY);
        KeyMappingHelper.registerKeyMapping(PLOT_KEY);

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(PlotGrid::render);

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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BuildBattleMode.reset();
            PlotGrid.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PinnedImage.clear());
    }

    /** Files a chat line into PinSpo and says whether the player should still see it. */
    private static boolean handle(Component message) {
        PinChat.onChatMessage(message);
        return !PinChat.isProtocolLine(message);
    }

    private static void onEndTick(Minecraft client) {
        while (GUIDE_KEY.consumeClick()) {
            PinGuide.Guide guide = PinnedImage.cycleGuide();
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable("message.pinspo.guide", guide.label()));
            }
        }
        PlotGrid.tick(client);
        while (PLOT_KEY.consumeClick()) {
            // Sneaking re-measures where the player stands instead of stepping to the next guide, because
            // the plot is otherwise measured once and left alone while the player moves around their build.
            boolean remeasure = client.player != null && client.player.isShiftKeyDown();
            if (remeasure) {
                PlotGrid.rescan();
            }
            PinGuide.Guide guide = remeasure ? PinSpoConfig.get().floorGuide : PlotGrid.cycle();
            if (client.player != null) {
                int[] size = PlotGrid.size();
                Component message;
                if (guide != PinGuide.Guide.OFF && size.length == 0) {
                    message = Component.translatable("message.pinspo.no_plot");
                } else if (remeasure) {
                    message = Component.translatable("message.pinspo.plot_measured", size[0], size[1]);
                } else {
                    message = Component.translatable("message.pinspo.plot_guide", guide.label());
                }
                client.player.sendOverlayMessage(message);
            }
        }
        while (OPEN_KEY.consumeClick()) {
            if (client.gui.screen() != null) {
                continue;
            }
            if (PinnedImage.isPinned()) {
                client.setScreenAndShow(new PinSettingsScreen(null));
            } else {
                client.setScreenAndShow(new PinBrowseScreen(null));
            }
        }
    }
}
