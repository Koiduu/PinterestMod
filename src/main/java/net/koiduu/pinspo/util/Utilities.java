package net.koiduu.pinspo.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.koiduu.pinspo.CameraRenderer;
import net.koiduu.pinspo.PinSpoClient;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Options for the Utilities tab: fog, far rendering and camera view range. Saved to config/pinspo-utilities.json. */
public final class Utilities {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pinspo-utilities.json");
    /** Fog pushed this far away is simply never reached. Start and end stay apart so the shader never divides by 0. */
    private static final float FAR = 1.0E7F;

    public static final float MAX_ENTITY_SCALE = 8.0F;
    public static final float MAX_CAMERA_RANGE = 4.0F;

    public boolean fog = true;
    public boolean distanceFog = true;
    public boolean skyFog = true;
    public boolean waterFog = true;
    public boolean lavaFog = true;
    public boolean snowFog = true;
    public boolean blindFog = true;
    public float entityScale = 1.0F;
    public float blockEntityScale = 1.0F;
    public float cameraRange = 1.0F;
    public boolean cameraFog = true;
    public boolean holograms = true;

    private static Utilities instance;

    public static Utilities get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Utilities load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                Utilities loaded = GSON.fromJson(reader, Utilities.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (Exception e) {
                PinSpoClient.LOGGER.warn("Could not read PinSpo utilities config, using defaults", e);
            }
        }
        return new Utilities();
    }

    public void save() {
        clamp();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            PinSpoClient.LOGGER.warn("Could not save PinSpo utilities config", e);
        }
    }

    private void clamp() {
        entityScale = Math.clamp(entityScale, 1.0F, MAX_ENTITY_SCALE);
        blockEntityScale = Math.clamp(blockEntityScale, 1.0F, MAX_ENTITY_SCALE);
        cameraRange = Math.clamp(cameraRange, 1.0F, MAX_CAMERA_RANGE);
    }

    /** Runs after the game has worked out this frame's fog, for the player's view and every camera view. */
    public static void adjustFog(FogData fog, FogType type, Entity entity) {
        Utilities u = get();
        boolean camera = CameraRenderer.isRendering();
        if (camera) {
            fog.renderDistanceStart *= u.cameraRange;
            fog.renderDistanceEnd *= u.cameraRange;
        }
        boolean all = u.fog && !(camera && !u.cameraFog);
        boolean blind = entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
        boolean environment = all && (blind ? u.blindFog : switch (type) {
            case WATER -> u.waterFog;
            case LAVA -> u.lavaFog;
            case POWDER_SNOW -> u.snowFog;
            default -> u.skyFog;
        });
        if (!environment) {
            fog.environmentalStart = FAR;
            fog.environmentalEnd = FAR + 1.0F;
        }
        if (!all || !u.distanceFog) {
            fog.renderDistanceStart = FAR;
            fog.renderDistanceEnd = FAR + 1.0F;
        }
    }

    public static double entityViewScale(double vanilla) {
        return vanilla * get().entityScale;
    }

    public static float blockEntityScale() {
        return get().blockEntityScale;
    }

    private Utilities() {
    }
}
