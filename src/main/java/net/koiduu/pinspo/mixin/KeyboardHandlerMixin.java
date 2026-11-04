package net.koiduu.pinspo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.koiduu.pinspo.util.Macros;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Macro keys are read from key events, so a quick tap between two ticks still counts. */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void pinspo$macroKeys(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            Macros.onKeyPress(event);
        }
    }
}
