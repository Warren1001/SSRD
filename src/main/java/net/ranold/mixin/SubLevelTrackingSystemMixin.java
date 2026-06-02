package net.ranold.mixin;

import dev.ryanhcode.sable.SableConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.ranold.SSRDForceloadData;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem", remap = false)
public class SubLevelTrackingSystemMixin {

    @Redirect(method = "shouldLoad", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D"))
    private double ssd$overrideTrackingRange(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue instance) {
        return 100000.0; // Return huge value to pass internal range checks
    }

    @Inject(method = "shouldLoad", at = @At("HEAD"), cancellable = true)
    private void ssd$checkPlayerRequestedRange(Player player, Vector3dc entityPosition, CallbackInfoReturnable<Boolean> cir) {
        double distSq = entityPosition.distanceSquared(player.getX(), player.getY(), player.getZ());
        
        // If forceloaded on server, always sync
        if (player instanceof ServerPlayer sp) {
            try {
                SSRDForceloadData data = net.ranold.SSRDForceloadData.get(sp.serverLevel());
                if (!data.getEntries().isEmpty()) {
                    dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = 
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sp.serverLevel());
                    
                    if (container != null) {
                        for (UUID uuid : data.getEntries().keySet()) {
                            Object sl = container.getSubLevel(uuid);
                            if (sl != null) {
                                try {
                                    Object pose = sl.getClass().getMethod("logicalPose").invoke(sl);
                                    double slX, slY, slZ;
                                    try {
                                        Object posObj = pose.getClass().getMethod("position").invoke(pose);
                                        slX = (double) posObj.getClass().getMethod("x").invoke(posObj);
                                        slY = (double) posObj.getClass().getMethod("y").invoke(posObj);
                                        slZ = (double) posObj.getClass().getMethod("z").invoke(posObj);
                                    } catch (Exception e) {
                                        slX = (double) pose.getClass().getMethod("x").invoke(pose);
                                        slY = (double) pose.getClass().getMethod("y").invoke(pose);
                                        slZ = (double) pose.getClass().getMethod("z").invoke(pose);
                                    }

                                    if (Math.abs(slX - entityPosition.x()) < 0.1 && 
                                        Math.abs(slY - entityPosition.y()) < 0.1 && 
                                        Math.abs(slZ - entityPosition.z()) < 0.1) {
                                        cir.setReturnValue(true);
                                        return;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        double range = net.ranold.Config.physicsTrackingRange; // Server's default
        if (player instanceof ServerPlayer sp) {
            Integer requested = net.ranold.ssrd.playerRequestedRanges.get(sp);
            if (requested != null) {
                range = requested * 16.0;
            }
        }

        boolean result = distSq < range * range;
        if (result && distSq > 320 * 320) {
            com.mojang.logging.LogUtils.getLogger().debug("SSRD: Allowing distant tracking for sub-level at {} for player {} (Range: {})", entityPosition, player.getName().getString(), range);
        }
        cir.setReturnValue(result);
    }
}
