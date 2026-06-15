package net.ranold.ssrd.mixin;

import net.ranold.ssrd.SSRDState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.viewport.Viewport", remap = false)
public class SodiumViewportMixin {

    @Inject(method = { "isBoxVisible", "isBoxVisibleLooser" }, at = @At("HEAD"), cancellable = true)
    private void ssd$bypassBoxVisibility(int intOriginX, int intOriginY, int intOriginZ, CallbackInfoReturnable<Boolean> cir) {
        if (SSRDState.IS_SUBLEVEL_RENDER.get()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isBoxVisibleDirect", at = @At("HEAD"), cancellable = true)
    private void ssd$bypassBoxVisibilityDirect(float floatOriginX, float floatOriginY, float floatOriginZ, float floatSize, CallbackInfoReturnable<Boolean> cir) {
        if (SSRDState.IS_SUBLEVEL_RENDER.get()) {
            cir.setReturnValue(true);
        }
    }
}
