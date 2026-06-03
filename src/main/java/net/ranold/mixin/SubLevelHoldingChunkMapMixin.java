package net.ranold.mixin;

import net.minecraft.world.level.ChunkPos;
import net.ranold.Config;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap", remap = false)
public abstract class SubLevelHoldingChunkMapMixin {

    @Shadow @Final private net.minecraft.server.level.ServerLevel level;

    @Inject(method = "updateChunkStatus", at = @At("HEAD"), cancellable = true)
    private void ssd$preventUnload(ChunkPos pos, boolean status, CallbackInfo ci) {
        if (!status) { // If unloading
            double centerX = (pos.x << 4) + 8;
            double centerZ = (pos.z << 4) + 8;

            for (var player : this.level.players()) {
                double range = net.ranold.Config.physicsTrackingRange;
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    // SSRD: Only use extended range if client has the mod
                    boolean hasMod = false;
                    try {
                        Object listener = sp.getClass().getField("connection").get(sp);
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
                        range = sp.requestedViewDistance() * 16.0; // Use vanilla view distance
                    } else {
                        Integer requested = net.ranold.ssrd.playerRequestedRanges.get(sp);
                        if (requested != null) {
                            range = requested * 16.0;
                        }
                    }
                }
                
                double rangeSq = range * range;
                double dx = player.getX() - centerX;
                double dz = player.getZ() - centerZ;
                if (dx * dx + dz * dz < rangeSq) {
                    com.mojang.logging.LogUtils.getLogger().debug("SSRD: Preventing sub-level unload for chunk {} near player {} (Range: {})", pos, player.getName().getString(), range);
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
