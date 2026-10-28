package net.koiduu.pinspo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The average colour of every full, opaque building block, read from the active resource pack's own
 * textures, so matches follow whatever pack the player builds with. Compared in OKLab, where distance
 * tracks how different two colours look rather than how different their numbers are.
 */
public final class BlockPalette {

    /** Blocks that are technical, tinted grey in their texture, or duplicates of another block's look. */
    private static final String[] SKIPPED = {
            "leaves", "grass_block", "command_block", "structure_block", "jigsaw", "barrier", "spawner",
            "infested_", "waxed_", "petrified", "test_block", "test_instance", "reinforced_deepslate"
    };

    public record Entry(Block block, int rgb, float l, float a, float b) {
    }

    public record Match(Block block, int rgb, int similarity) {
    }

    @Nullable
    private static volatile List<Entry> entries;
    private static boolean building;

    private BlockPalette() {
    }

    public static boolean isReady() {
        return entries != null;
    }

    /** Starts reading the textures in the background the first time anything asks. */
    public static void prepare() {
        if (entries != null || building) {
            return;
        }
        building = true;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        CompletableFuture.supplyAsync(() -> build(resources)).whenComplete((built, error) -> {
            if (error != null) {
                PinSpoClient.LOGGER.warn("Could not build the block colour palette", error);
                built = List.of();
            }
            entries = built;
            PinSpoClient.LOGGER.info("Block colour palette ready: {} blocks", built.size());
        });
    }

    public static List<Match> nearest(int rgb, int count) {
        List<Entry> palette = entries;
        if (palette == null) {
            prepare();
            return List.of();
        }
        float[] lab = oklab(rgb);
        List<Entry> sorted = new ArrayList<>(palette);
        sorted.sort(Comparator.comparingDouble(entry -> distance(lab, entry)));
        List<Match> matches = new ArrayList<>();
        for (Entry entry : sorted.subList(0, Math.min(count, sorted.size()))) {
            double distance = Math.sqrt(distance(lab, entry));
            int similarity = (int) Math.round(100.0D * Math.max(0.0D, 1.0D - distance / 0.35D));
            matches.add(new Match(entry.block(), entry.rgb(), similarity));
        }
        return matches;
    }

    private static double distance(float[] lab, Entry entry) {
        float dl = lab[0] - entry.l();
        float da = lab[1] - entry.a();
        float db = lab[2] - entry.b();
        return dl * dl + da * da + db * db;
    }

    private static List<Entry> build(ResourceManager resources) {
        List<Entry> built = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.asItem() == Items.AIR) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            if (!state.isSolidRender()) {
                continue;
            }
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String path = id.getPath();
            if (skipped(path)) {
                continue;
            }
            Integer rgb = averageColour(resources, id.getNamespace(), path);
            if (rgb != null) {
                float[] lab = oklab(rgb);
                built.add(new Entry(block, rgb, lab[0], lab[1], lab[2]));
            }
        }
        return List.copyOf(built);
    }

    private static boolean skipped(String path) {
        for (String skip : SKIPPED) {
            if (path.contains(skip)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Integer averageColour(ResourceManager resources, String namespace, String path) {
        String base = path.endsWith("_wood") ? path.substring(0, path.length() - 5) + "_log"
                : path.endsWith("_hyphae") ? path.substring(0, path.length() - 7) + "_stem"
                : path;
        for (String candidate : new String[]{base, base + "_side", base + "_front", base + "_top",
                base.replace("_block", ""), base + "_block"}) {
            Optional<Resource> resource = resources.getResource(
                    Identifier.fromNamespaceAndPath(namespace, "textures/block/" + candidate + ".png"));
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                return average(image);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** Mean of the opaque pixels of the first animation frame; null for see-through textures. */
    @Nullable
    private static Integer average(NativeImage image) {
        int width = image.getWidth();
        int height = Math.min(width, image.getHeight());
        long red = 0;
        long green = 0;
        long blue = 0;
        int opaque = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixel(x, y);
                if ((pixel >>> 24) < 128) {
                    continue;
                }
                red += pixel >> 16 & 0xFF;
                green += pixel >> 8 & 0xFF;
                blue += pixel & 0xFF;
                opaque++;
            }
        }
        if (opaque < width * height * 0.9F) {
            return null;
        }
        return (int) (red / opaque) << 16 | (int) (green / opaque) << 8 | (int) (blue / opaque);
    }

    public static float[] oklab(int rgb) {
        double r = linear(rgb >> 16 & 0xFF);
        double g = linear(rgb >> 8 & 0xFF);
        double b = linear(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new float[]{
                (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s),
                (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
                (float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
        };
    }

    private static double linear(int channel) {
        double c = channel / 255.0D;
        return c <= 0.04045D ? c / 12.92D : Math.pow((c + 0.055D) / 1.055D, 2.4D);
    }
}
