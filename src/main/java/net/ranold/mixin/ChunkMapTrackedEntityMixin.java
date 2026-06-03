package net.ranold.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.ranold.Config;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow @Final Entity entity;
    @Shadow int range;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ssd$overrideRange(CallbackInfo ci) {
        String name = EntityType.getKey(this.entity.getType()).toString();
        boolean isContraption = name.startsWith("create:") || name.startsWith("aeronautics:") || name.startsWith("offroad:");
        if (isContraption && (name.contains("contraption") || name.contains("carriage") || name.contains("propeller"))) {
            int requestedRange = (int) Config.physicsTrackingRange;
            if (requestedRange > this.range) {
                this.range = requestedRange;
                com.mojang.logging.LogUtils.getLogger().info("SSRD: ChunkMapTrackedEntityMixin applied! Range for {} increased to {}", name, requestedRange);
            }
        }
    }

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private int ssd$bypassViewDistanceClamp(int range, int viewDistanceBlocks, ServerPlayer player) {
        String name = EntityType.getKey(this.entity.getType()).toString();
        boolean isContraption = name.startsWith("create:") || name.startsWith("aeronautics:") || name.startsWith("offroad:");
        if (isContraption && (name.contains("contraption") || name.contains("carriage") || name.contains("propeller"))) {
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

            if (hasMod) {
                return range; // Ignore the viewDistance clamp
            }
        }
        return Math.min(range, viewDistanceBlocks);
    }
}
