package com.extendedae_plus.util.storage;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** Persistent storage manager for all infinity disks in the current world. */
public class InfinityStorageManager extends SavedData {
    private final Object2ObjectMap<UUID, InfinityDataStorage> cells;
    // Changes only when a cell object is added, replaced, or removed. Inventory
    // instances use this to invalidate their object cache without map lookups.
    private long storageRevision;

    public InfinityStorageManager() {
        this.cells = new Object2ObjectOpenHashMap<>();
        this.setDirty();
    }

    private InfinityStorageManager(Object2ObjectMap<UUID, InfinityDataStorage> cells) {
        this.cells = cells;
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        ListTag cellList = new ListTag();
        for (var entry : Object2ObjectMaps.fastIterable(this.cells)) {
            CompoundTag cell = new CompoundTag();
            cell.putUUID(InfinityConstants.INFINITY_CELL_UUID, entry.getKey());
            cell.put(InfinityConstants.INFINITY_CELL_DATA, entry.getValue().serializeNBT());
            cellList.add(cell);
        }
        nbt.put(InfinityConstants.INFINITY_CELL_LIST, cellList);
        nbt.putInt(InfinityConstants.FORMAT_VERSION_FIELD, InfinityConstants.FORMAT_VERSION);
        return nbt;
    }

    public static InfinityStorageManager readNbt(CompoundTag nbt) {
        ListTag cellList = nbt.getList(InfinityConstants.INFINITY_CELL_LIST, CompoundTag.TAG_COMPOUND);
        Object2ObjectMap<UUID, InfinityDataStorage> cells =
                new Object2ObjectOpenHashMap<>(Math.max(2, cellList.size()));
        for (int i = 0; i < cellList.size(); i++) {
            CompoundTag cell = cellList.getCompound(i);
            cells.put(
                    cell.getUUID(InfinityConstants.INFINITY_CELL_UUID),
                    InfinityDataStorage.loadFromNBT(cell.getCompound(InfinityConstants.INFINITY_CELL_DATA))
            );
        }
        return new InfinityStorageManager(cells);
    }

    public Set<UUID> getAllLoadedUUIDs() {
        return Collections.unmodifiableSet(this.cells.keySet());
    }

    public void updateCell(UUID uuid, InfinityDataStorage storage) {
        InfinityDataStorage previous = this.cells.put(uuid, storage);
        if (previous != storage) {
            this.storageRevision++;
        }
        this.setDirty();
    }

    public void removeCell(UUID uuid) {
        if (this.cells.remove(uuid) != null) {
            this.storageRevision++;
            this.setDirty();
        }
    }

    public boolean hasUUID(UUID uuid) {
        return this.cells.containsKey(uuid);
    }

    public InfinityDataStorage getCell(UUID uuid) {
        return this.cells.get(uuid);
    }

    public long getStorageRevision() {
        return this.storageRevision;
    }

    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        InfinityDataStorage cell = this.cells.get(uuid);
        if (cell == null) {
            cell = new InfinityDataStorage();
            this.cells.put(uuid, cell);
            this.storageRevision++;
            this.setDirty();
        }
        return cell;
    }

    public static InfinityStorageManager getInstance(MinecraftServer server) {
        ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
        return world.getDataStorage().computeIfAbsent(
                InfinityStorageManager::readNbt,
                InfinityStorageManager::new,
                InfinityConstants.SAVE_FILE_NAME
        );
    }
}
