package net.ranold.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow @Final private net.minecraft.server.level.ServerLevel level;

    @Inject(method = "isChunkTracked", at = @At("RETURN"), cancellable = true)
    private void ssd$forceTrackContraptionChunks(ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        // If vanilla already tracks it, do nothing
        if (cir.getReturnValue()) return;

        // Use pure reflection to avoid loading problematic Sable classes on server
        try {
            // Get container from level directly
            Object container = null;
            for (java.lang.reflect.Method m : this.level.getClass().getMethods()) {
                if (m.getName().equals("sable$getPlotContainer")) {
                    m.setAccessible(true);
                    container = m.invoke(this.level);
                    break;
                }
            }
            if (container == null) return;

            ChunkPos targetPos = new ChunkPos(x, z);
            
            java.lang.reflect.Field allSubLevelsField = container.getClass().getSuperclass().getDeclaredField("allSubLevels");
            allSubLevelsField.setAccessible(true);
            Iterable<?> allSubLevels = (Iterable<?>) allSubLevelsField.get(container);
            
            for (Object slObj : allSubLevels) {
                // Access trackingPlayers field on ServerSubLevel
                java.lang.reflect.Field trackingPlayersField = slObj.getClass().getDeclaredField("trackingPlayers");
                trackingPlayersField.setAccessible(true);
                java.util.Collection<java.util.UUID> trackingPlayers = (java.util.Collection<java.util.UUID>) trackingPlayersField.get(slObj);
                
                if (trackingPlayers.contains(player.getUUID())) {
                    // SSRD: Only force chunk tracking if player has the mod
                    boolean hasMod = false;
                    try {
                        Object listener = player.getClass().getField("connection").get(player);
                        java.lang.reflect.Field connField = listener.getClass().getSuperclass().getDeclaredField("connection");
                        connField.setAccessible(true);
                        Object connection = connField.get(listener);
                        
                        java.lang.reflect.Field channelField = connection.getClass().getDeclaredField("channel");
                        channelField.setAccessible(true);
                        io.netty.channel.Channel channel = (io.netty.channel.Channel) channelField.get(connection);
                        
                        var networkRegistryClass = Class.forName("net.neoforged.neoforge.network.registration.NetworkRegistry");
                        var channelsAttrField = networkRegistryClass.getField("CHANNELS_ATTRIBUTE");
                        var channelsAttrKey = (io.netty.util.AttributeKey<java.util.Map<net.minecraft.resources.ResourceLocation, ?>>) channelsAttrField.get(null);
                        
                        var attr = channel.attr(channelsAttrKey).get();
                        if (attr != null && attr.containsKey(net.ranold.ServerConfigSyncPacket.TYPE.id())) {
                            hasMod = true;
                        }
                    } catch (Exception ignored) {}

                    if (!hasMod) {
                        continue;
                    }

                    // Access plot field on SubLevel
                    java.lang.reflect.Field plotField = slObj.getClass().getSuperclass().getDeclaredField("plot");
                    plotField.setAccessible(true);
                    Object plot = plotField.get(slObj);
                    
                    // Access contraptions field on ServerLevelPlot
                    java.lang.reflect.Field contraptionsField = plot.getClass().getDeclaredField("contraptions");
                    contraptionsField.setAccessible(true);
                    java.util.Collection<?> contraptions = (java.util.Collection<?>) contraptionsField.get(plot);
                    
                    for (Object cObj : contraptions) {
                        if (cObj instanceof Entity entity) {
                            if (entity.chunkPosition().equals(targetPos)) {
                                cir.setReturnValue(true);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in forceTrackContraptionChunks", e);
        }
    }
}
