package net.ranold.ssrd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.engine_room.flywheel.impl.visual.BandedPrimeLimiter", remap = false)
public class BandedPrimeLimiterMixin {

    @Inject(method = "shouldUpdate", at = @At("HEAD"), cancellable = true)
    private void ssd$alwaysUpdate(double distanceSquared, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
