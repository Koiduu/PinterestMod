package net.koiduu.pinspo.build;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashSet;
import java.util.Set;

/** Edge detection for raw keys, so a held Ctrl+Z undoes once instead of every tick. */
final class BuildKeys {

    private static final Set<Integer> HELD = new HashSet<>();
    private static final Set<Integer> SEEN = new HashSet<>();

    private BuildKeys() {
    }

    static boolean pressed(int key) {
        boolean down = InputConstants.isKeyDown(key);
        SEEN.add(key);
        if (!down) {
            HELD.remove(key);
            return false;
        }
        return HELD.add(key);
    }

    static void endTick() {
        for (int key : new HashSet<>(HELD)) {
            if (!InputConstants.isKeyDown(key)) {
                HELD.remove(key);
            }
        }
        SEEN.clear();
    }
}
