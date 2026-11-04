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
import net.koiduu.pinspo.build.BuildTools;
import net.koiduu.pinspo.build.BuildToolsScreen;
import net.koiduu.pinspo.util.Holograms;
import net.koiduu.pinspo.util.Macros;
import net.koiduu.pinspo.util.ShadeScreen;
import net.koiduu.pinspo.util.Utilities;
import net.koiduu.pinspo.util.UtilitiesScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PinSpoClient implements ClientModInitializer {

    public static final String MOD_ID = "pinspo";
    public static final Logger LOGGER = LoggerFactory.getLogger("PinSpo");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "pinspo"));

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.pinspo.open",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_M,
            CATEGORY
    );

    /** Puts the pinned image on the wall being looked at, or moves it there; sneak to edit it instead. */
    public static final KeyMapping WALL_KEY = new KeyMapping(
            "key.pinspo.wall",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_B,
            CATEGORY
    );

    /** Plants a live camera where the player is looking; sneak to arrange the camera windows instead. */
    public static final KeyMapping BUILD_KEY = new KeyMapping(
            "key.pinspo.build",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N,
            CATEGORY
    );

    public static final KeyMapping CAMERA_KEY = new KeyMapping(
            "key.pinspo.camera",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            CATEGORY
    );


    /** Shows or hides every camera window in this world. */
    public static final KeyMapping CAMERAS_TOGGLE_KEY = new KeyMapping(
            "key.pinspo.cameras_toggle",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_J,
            CATEGORY
    );

    /** Shows or hides the pinned reference. */
    public static final KeyMapping REFERENCE_KEY = new KeyMapping(
            "key.pinspo.reference",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H,
            CATEGORY
    );

    /** Teleports to the next camera (needs /tp). */
    public static final KeyMapping CAMERA_TP_KEY = new KeyMapping(
            "key.pinspo.camera_tp",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_U,
            CATEGORY
    );

    /** Saves a screenshot of every camera window. */
    public static final KeyMapping CAMERA_SHOT_KEY = new KeyMapping(
            "key.pinspo.camera_shot",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_I,
            CATEGORY
    );

    /** Adds a hologram line point; sneak to finish the line. */
    public static final KeyMapping HOLOGRAM_KEY = new KeyMapping(
            "key.pinspo.hologram",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_R,
            CATEGORY
    );

    /** Turns all fog on or off. */
    public static final KeyMapping FOG_KEY = new KeyMapping(
            "key.pinspo.fog",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Y,
            CATEGORY
    );

    /** Opens the shade shifter on the block being looked at. */
    public static final KeyMapping SHADE_KEY = new KeyMapping(
            "key.pinspo.shade",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Z,
            CATEGORY
    );

    /** Opens the Utilities tab. */
    public static final KeyMapping UTILITIES_KEY = new KeyMapping(
            "key.pinspo.utilities",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            CATEGORY
    );

    /** Every PinSpo key, in the order the Keys page lists them. */
    public static java.util.List<KeyMapping> keys() {
        java.util.List<KeyMapping> all = new java.util.ArrayList<>(java.util.List.of(OPEN_KEY, WALL_KEY, CAMERA_KEY,
                CAMERAS_TOGGLE_KEY, CAMERA_TP_KEY, CAMERA_SHOT_KEY, REFERENCE_KEY, HOLOGRAM_KEY, SHADE_KEY, FOG_KEY,
                UTILITIES_KEY));
        if (BuildTools.ENABLED) {
            all.add(BUILD_KEY);
        }
        return all;
    }

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(OPEN_KEY);
        KeyMappingHelper.registerKeyMapping(WALL_KEY);
        KeyMappingHelper.registerKeyMapping(CAMERA_KEY);
        KeyMappingHelper.registerKeyMapping(CAMERAS_TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(REFERENCE_KEY);
        KeyMappingHelper.registerKeyMapping(CAMERA_TP_KEY);
        KeyMappingHelper.registerKeyMapping(CAMERA_SHOT_KEY);
        KeyMappingHelper.registerKeyMapping(HOLOGRAM_KEY);
        KeyMappingHelper.registerKeyMapping(FOG_KEY);
        KeyMappingHelper.registerKeyMapping(SHADE_KEY);
        KeyMappingHelper.registerKeyMapping(UTILITIES_KEY);
        if (BuildTools.ENABLED) {
            KeyMappingHelper.registerKeyMapping(BUILD_KEY);
            ClientTickEvents.START_CLIENT_TICK.register(BuildTools::onStartTick);
        }
        // The shade shifter edits through the build tools engine (direct, /fill, or ghost blocks), so its
        // command queue and ghost drawing run even with the build mode itself switched off.
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(BuildTools::render);
        ClientTickEvents.END_CLIENT_TICK.register(BuildTools::onEndTick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(Holograms::render);

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WallPhotos::render);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "pinned_image"),
                (guiGraphics, deltaTracker) -> {
                    CameraPoints.render(guiGraphics);
                    if (BuildTools.ENABLED) {
                        BuildTools.renderHud(guiGraphics);
                    }
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
            WallPhotos.onDisconnect();
            CameraRenderer.releaseAll();
            BuildTools.onDisconnect();
            Holograms.onDisconnect();
            Macros.stopAll();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PinnedImage.clear());
    }

    /** Files a chat line into PinSpo and says whether the player should still see it. */
    private static boolean handle(Component message) {
        PinChat.onChatMessage(message);
        return !PinChat.isProtocolLine(message);
    }

    private static void onEndTick(Minecraft client) {
        boolean sneaking = client.player != null && client.player.isShiftKeyDown();
        while (WALL_KEY.consumeClick()) {
            if (client.gui.screen() != null) {
                continue;
            }
            if (sneaking && WallPhotos.mine() != null) {
                client.setScreenAndShow(new WallEditScreen(null));
            } else {
                WallPhotos.placeAtCrosshair(client);
            }
        }
        while (CAMERA_KEY.consumeClick()) {
            if (client.gui.screen() != null) {
                continue;
            }
            if (sneaking) {
                client.setScreenAndShow(new CameraLayoutScreen(null));
            } else {
                CameraPoints.plant(client);
            }
        }
        while (BUILD_KEY.consumeClick()) {
            if (client.gui.screen() != null) {
                continue;
            }
            if (sneaking) {
                client.setScreenAndShow(new BuildToolsScreen(null));
            } else {
                BuildTools.toggle();
            }
        }
        while (CAMERAS_TOGGLE_KEY.consumeClick()) {
            if (free(client)) {
                CameraPoints.toggleAll();
            }
        }
        while (CAMERA_TP_KEY.consumeClick()) {
            if (free(client)) {
                CameraPoints.teleportNext(client);
            }
        }
        while (CAMERA_SHOT_KEY.consumeClick()) {
            if (free(client)) {
                CameraPoints.screenshotAll();
            }
        }
        while (REFERENCE_KEY.consumeClick()) {
            if (!free(client)) {
                continue;
            }
            if (!PinnedImage.isPinned()) {
                overlay(client, Component.translatable("message.pinspo.reference_none", OPEN_KEY.getTranslatedKeyMessage()));
            } else {
                PinnedImage.setHidden(!PinnedImage.isHidden());
                overlay(client, Component.translatable(PinnedImage.isHidden()
                        ? "message.pinspo.reference_hidden" : "message.pinspo.reference_shown"));
            }
        }
        while (HOLOGRAM_KEY.consumeClick()) {
            if (!free(client)) {
                continue;
            }
            if (sneaking) {
                Holograms.finish();
            } else {
                Holograms.place(client);
            }
        }
        while (FOG_KEY.consumeClick()) {
            if (free(client)) {
                Utilities settings = Utilities.get();
                settings.fog = !settings.fog;
                settings.save();
                overlay(client, Component.translatable(settings.fog ? "message.pinspo.fog_on" : "message.pinspo.fog_off"));
            }
        }
        while (SHADE_KEY.consumeClick()) {
            if (free(client)) {
                Component problem = ShadeScreen.openAtCrosshair(client, null);
                if (problem != null) {
                    overlay(client, problem);
                }
            }
        }
        while (UTILITIES_KEY.consumeClick()) {
            if (free(client)) {
                client.setScreenAndShow(new UtilitiesScreen(null));
            }
        }
        Holograms.tick(client);
        Macros.tick(client);
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

    private static boolean free(Minecraft client) {
        return client.gui.screen() == null && client.player != null;
    }

    private static void overlay(Minecraft client, Component message) {
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }
}
