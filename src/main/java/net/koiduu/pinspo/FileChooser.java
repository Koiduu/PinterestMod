package net.koiduu.pinspo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * The system "open file" dialog. Minecraft 26.3 runs on SDL3, so this uses SDL's own dialog: it returns
 * at once and calls back when the player picks a file or cancels, always on the render thread here.
 */
public final class FileChooser {

    @Nullable
    private static SDL_DialogFileCallback callback;
    @Nullable
    private static SDL_DialogFileFilter.Buffer filters;
    @Nullable
    private static ByteBuffer filterName;
    @Nullable
    private static ByteBuffer filterPattern;

    private FileChooser() {
    }

    /** Opens the dialog; {@code onResult} gets the chosen path, or {@code null} when nothing was picked. */
    public static void open(Consumer<String> onResult) {
        Minecraft client = Minecraft.getInstance();
        if (callback != null) {
            // One dialog at a time; a second click while it is open does nothing.
            return;
        }
        try {
            filterName = MemoryUtil.memUTF8("Images");
            filterPattern = MemoryUtil.memUTF8("png;jpg;jpeg;webp;gif");
            filters = SDL_DialogFileFilter.calloc(1);
            filters.get(0).name(filterName).pattern(filterPattern);
            callback = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
                String chosen = null;
                if (fileList != 0L) {
                    long first = MemoryUtil.memGetAddress(fileList);
                    if (first != 0L) {
                        chosen = MemoryUtil.memUTF8(first);
                    }
                }
                String result = chosen;
                client.execute(() -> {
                    release();
                    onResult.accept(result);
                });
            });
            SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, client.getWindow().handle(), filters,
                    (CharSequence) null, false);
        } catch (Throwable e) {
            PinSpoClient.LOGGER.warn("Could not open the system file chooser", e);
            release();
            onResult.accept(null);
        }
    }

    private static void release() {
        if (callback != null) {
            callback.free();
            callback = null;
        }
        if (filters != null) {
            filters.free();
            filters = null;
        }
        if (filterName != null) {
            MemoryUtil.memFree(filterName);
            filterName = null;
        }
        if (filterPattern != null) {
            MemoryUtil.memFree(filterPattern);
            filterPattern = null;
        }
    }

    static Component title() {
        return Component.translatable("screen.pinspo.choose_file_title");
    }
}
