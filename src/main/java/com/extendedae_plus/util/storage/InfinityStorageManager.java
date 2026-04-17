package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityStorageManager extends SavedData {

    // 存储所有磁盘的Map，键为UUID，值为DataStorage对象
    private final Map<UUID, InfinityDataStorage> cells;

    public InfinityStorageManager() {
        cells = new HashMap<>();
        this.setDirty();
    }

    private InfinityStorageManager(Map<UUID, InfinityDataStorage> cells) {
        this.cells = cells;
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        ListTag cellList = new ListTag();
        for (Map.Entry<UUID, InfinityDataStorage> entry : cells.entrySet()) {
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
        int version = nbt.contains(InfinityConstants.FORMAT_VERSION_FIELD) ?
                nbt.getInt(InfinityConstants.FORMAT_VERSION_FIELD) :
                1;

        Map<UUID, InfinityDataStorage> cells = new HashMap<>();
        ListTag cellList = nbt.getList(InfinityConstants.INFINITY_CELL_LIST, CompoundTag.TAG_COMPOUND);
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
        return Collections.unmodifiableSet(cells.keySet());
    }

    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        cells.put(uuid, infinityDataStorage);
        setDirty();
    }

    public void removeCell(UUID uuid) {
        cells.remove(uuid);
        setDirty();
    }

    public boolean hasUUID(UUID uuid) {
        return cells.containsKey(uuid);
    }

    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        if (!cells.containsKey(uuid)) {
            updateCell(uuid, new InfinityDataStorage());
        }
        return cells.get(uuid);
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
