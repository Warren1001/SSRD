package net.ranold.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;inEntityTickingRange(J)Z"))
    private boolean ssd$forceTickContraptionChunks(DistanceManager instance, long l, Operation<Boolean> original) {
        // If vanilla says it's in range, use that
        if (original.call(instance, l)) return true;

        final ChunkPos chunkPos = new ChunkPos(l);
        
        try {
            // Get container from level directly
            Object container = null;
            for (java.lang.reflect.Method m : ((net.minecraft.world.level.Level) (Object) this).getClass().getMethods()) {
                if (m.getName().equals("sable$getPlotContainer")) {
                    m.setAccessible(true);
                    container = m.invoke((net.minecraft.world.level.Level) (Object) this);
                    break;
                }
            }
            if (container == null) return false;

            java.lang.reflect.Field allSubLevelsField = container.getClass().getSuperclass().getDeclaredField("allSubLevels");
            allSubLevelsField.setAccessible(true);
            Iterable<?> allSubLevels = (Iterable<?>) allSubLevelsField.get(container);
            
            // If any active sub-level has a contraption in this chunk, force tick it
            for (Object slObj : allSubLevels) {
                // Access plot field on SubLevel
                java.lang.reflect.Field plotField = slObj.getClass().getSuperclass().getDeclaredField("plot");
                plotField.setAccessible(true);
                Object plot = plotField.get(slObj);
                
                // Access contraptions field on ServerLevelPlot
                java.lang.reflect.Field contraptionsField = plot.getClass().getDeclaredField("contraptions");
                contraptionsField.setAccessible(true);
                java.util.Collection<?> contraptions = (java.util.Collection<?>) contraptionsField.get(plot);
                
                for (Object contraptionObj : contraptions) {
                    if (contraptionObj instanceof Entity e) {
                        if (e.chunkPosition().equals(chunkPos)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in forceTickContraptionChunks", e);
        }

        return false;
    }
}
