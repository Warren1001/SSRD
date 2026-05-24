package net.ranold.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void ssd$forceRenderContraptions(E entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        String className = entity.getClass().getName();
        if (className.contains("Contraption") || className.contains("Carriage")) {
            try {
                Object helper = dev.ryanhcode.sable.Sable.HELPER;
                Object containing = helper.getClass().getMethod("getContaining", Entity.class).invoke(helper, entity);
                if (containing != null && containing.getClass().getName().contains("ClientSubLevel")) {
                    cir.setReturnValue(true);
                    return;
                }
                Object tracking = helper.getClass().getMethod("getTrackingSubLevel", Entity.class).invoke(helper, entity);
                if (tracking != null && tracking.getClass().getName().contains("ClientSubLevel")) {
                    cir.setReturnValue(true);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
