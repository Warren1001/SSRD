package net.ranold;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SSRDForceloadData extends SavedData {
    private final Map<UUID, ForceloadEntry> entries = new HashMap<>();

    public static class ForceloadEntry {
        public final UUID uuid;
        public ResourceLocation dimension;
        public ChunkPos pos;

        public ForceloadEntry(UUID uuid, ResourceLocation dimension, ChunkPos pos) {
            this.uuid = uuid;
            this.dimension = dimension;
            this.pos = pos;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            if (dimension != null) tag.putString("Dimension", dimension.toString());
            if (pos != null) {
                tag.putInt("CX", pos.x);
                tag.putInt("CZ", pos.z);
            }
            return tag;
        }

        public static ForceloadEntry load(CompoundTag tag) {
            UUID uuid = tag.getUUID("UUID");
            ResourceLocation dim = tag.contains("Dimension") ? ResourceLocation.parse(tag.getString("Dimension")) : null;
            ChunkPos pos = tag.contains("CX") ? new ChunkPos(tag.getInt("CX"), tag.getInt("CZ")) : null;
            return new ForceloadEntry(uuid, dim, pos);
        }
    }

    public SSRDForceloadData() {
    }

    public static SSRDForceloadData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        SSRDForceloadData data = new SSRDForceloadData();
        
        // Load new format
        if (tag.contains("Entries", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ForceloadEntry entry = ForceloadEntry.load(list.getCompound(i));
                data.entries.put(entry.uuid, entry);
            }
        }
        
        // Load legacy format
        if (tag.contains("ForceloadedSubLevels", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ForceloadedSubLevels", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    UUID uuid = UUID.fromString(list.getString(i));
                    data.entries.computeIfAbsent(uuid, k -> new ForceloadEntry(k, null, null));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        ListTag list = new ListTag();
        for (ForceloadEntry entry : entries.values()) {
            list.add(entry.save());
        }
        tag.put("Entries", list);
        return tag;
    }

    public Map<UUID, ForceloadEntry> getEntries() {
        return entries;
    }

    public static SSRDForceloadData get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(SSRDForceloadData::new, SSRDForceloadData::load),
                "ssrd_forceload"
        );
    }
}
