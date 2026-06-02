package net.ranold;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "ssrd")
public class SSRDForceloadManager {

    private static final Map<ServerLevel, Map<UUID, Set<ChunkPos>>> FORCED_CHUNKS = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return;

        try {
            // Use API to get container
            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = 
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            
            if (container == null) return;

            SSRDForceloadData data = SSRDForceloadData.get(level);
            Map<UUID, SSRDForceloadData.ForceloadEntry> entries = data.getEntries();

            Map<UUID, Set<ChunkPos>> requiredChunks = new HashMap<>();
            boolean dataDirty = false;

            for (SSRDForceloadData.ForceloadEntry entry : entries.values()) {
                Object subLevel = container.getSubLevel(entry.uuid);
                
                boolean isRemoved = true;
                if (subLevel != null) {
                    try {
                        isRemoved = (boolean) subLevel.getClass().getMethod("isRemoved").invoke(subLevel);
                    } catch (Exception ignored) {}
                }

                if (subLevel != null && !isRemoved) {
                    // SubLevel is loaded in this level
                    Object bounds = null;
                    try {
                        bounds = subLevel.getClass().getMethod("boundingBox").invoke(subLevel);
                    } catch (Exception ignored) {}
                    
                    if (bounds != null) {
                        double bMinX = (double) bounds.getClass().getMethod("minX").invoke(bounds);
                        double bMinZ = (double) bounds.getClass().getMethod("minZ").invoke(bounds);
                        double bMaxX = (double) bounds.getClass().getMethod("maxX").invoke(bounds);
                        double bMaxZ = (double) bounds.getClass().getMethod("maxZ").invoke(bounds);

                        // Adaptive look-ahead based on velocity
                        try {
                            // latestLinearVelocity is public in ServerSubLevel. Using reflection to handle possible obfuscation.
                            java.lang.reflect.Field velField = null;
                            Class<?> current = subLevel.getClass();
                            while (current != null && velField == null) {
                                try {
                                    velField = current.getDeclaredField("latestLinearVelocity");
                                } catch (NoSuchFieldException e) {
                                    current = current.getSuperclass();
                                }
                            }
                            
                            if (velField != null) {
                                velField.setAccessible(true);
                                Object velocity = velField.get(subLevel);
                                
                                java.lang.reflect.Field xField = velocity.getClass().getField("x");
                                java.lang.reflect.Field zField = velocity.getClass().getField("z");
                                
                                double velX = xField.getDouble(velocity);
                                double velZ = zField.getDouble(velocity);
                                
                                if (velX > 0) bMaxX += velX; else bMinX += velX;
                                if (velZ > 0) bMaxZ += velZ; else bMinZ += velZ;
                            }
                        } catch (Exception ignored) {}

                        Set<ChunkPos> chunks = new HashSet<>();
                        int minCX = (net.minecraft.util.Mth.floor(bMinX) >> 4) - 1;
                        int minCZ = (net.minecraft.util.Mth.floor(bMinZ) >> 4) - 1;
                        int maxCX = (net.minecraft.util.Mth.floor(bMaxX) >> 4) + 1;
                        int maxCZ = (net.minecraft.util.Mth.floor(bMaxZ) >> 4) + 1;

                        for (int x = minCX; x <= maxCX; x++) {
                            for (int z = minCZ; z <= maxCZ; z++) {
                                chunks.add(new ChunkPos(x, z));
                            }
                        }
                        requiredChunks.put(entry.uuid, chunks);
                        
                        // Update last known position and dimension
                        ResourceLocation currentDim = level.dimension().location();
                        ChunkPos currentPos = new ChunkPos((int)((bMinX + bMaxX)/2) >> 4, (int)((bMinZ + bMaxZ)/2) >> 4);
                        if (!currentDim.equals(entry.dimension) || !currentPos.equals(entry.pos)) {
                            entry.dimension = currentDim;
                            entry.pos = currentPos;
                            dataDirty = true;
                        }
                    }
                } else if (entry.dimension != null && entry.dimension.equals(level.dimension().location()) && entry.pos != null) {
                    // SubLevel is not loaded, but we have a last known position in THIS dimension
                    Set<ChunkPos> chunks = new HashSet<>();
                    for (int x = entry.pos.x - 1; x <= entry.pos.x + 1; x++) {
                        for (int z = entry.pos.z - 1; z <= entry.pos.z + 1; z++) {
                            chunks.add(new ChunkPos(x, z));
                        }
                    }
                    requiredChunks.put(entry.uuid, chunks);
                }
            }

            if (dataDirty) {
                data.setDirty();
            }

            Map<UUID, Set<ChunkPos>> levelForced = FORCED_CHUNKS.computeIfAbsent(level, k -> new HashMap<>());
            Set<UUID> allTrackedUuids = new HashSet<>(levelForced.keySet());
            allTrackedUuids.addAll(requiredChunks.keySet());

            for (UUID uuid : allTrackedUuids) {
                Set<ChunkPos> currentlyForced = levelForced.getOrDefault(uuid, new HashSet<>());
                Set<ChunkPos> required = requiredChunks.getOrDefault(uuid, new HashSet<>());

                for (ChunkPos pos : currentlyForced) {
                    if (!required.contains(pos)) {
                        level.setChunkForced(pos.x, pos.z, false);
                    }
                }

                for (ChunkPos pos : required) {
                    if (!currentlyForced.contains(pos)) {
                        level.setChunkForced(pos.x, pos.z, true);
                    }
                }

                if (required.isEmpty()) {
                    levelForced.remove(uuid);
                } else {
                    levelForced.put(uuid, required);
                }
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in forceload tick", e);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FORCED_CHUNKS.remove(level);
        }
    }
}
