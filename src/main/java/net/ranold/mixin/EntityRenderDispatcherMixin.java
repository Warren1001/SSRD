package net.ranold.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.ranold.SSRDState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void ssd$forceRenderContraptions(E entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (ssd$isSubLevelEntity(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void ssd$preRenderEntity(E entity, double x, double y, double z, float yRot, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (ssd$isSubLevelEntity(entity)) {
            SSRDState.IS_SUBLEVEL_RENDER.set(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void ssd$postRenderEntity(E entity, double x, double y, double z, float yRot, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (ssd$isSubLevelEntity(entity)) {
            SSRDState.IS_SUBLEVEL_RENDER.set(false);
        }
    }

    private boolean ssd$isSubLevelEntity(Entity entity) {
        String className = entity.getClass().getName();
        if (className.contains("Contraption") || className.contains("Carriage")) {
            try {
                Object helper = dev.ryanhcode.sable.Sable.HELPER;
                Object containing = helper.getClass().getMethod("getContaining", Entity.class).invoke(helper, entity);
                if (containing != null && containing.getClass().getName().contains("ClientSubLevel")) {
                    return true;
                }
                Object tracking = helper.getClass().getMethod("getTrackingSubLevel", Entity.class).invoke(helper, entity);
                if (tracking != null && tracking.getClass().getName().contains("ClientSubLevel")) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return false;
    }
}
