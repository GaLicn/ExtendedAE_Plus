package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.*;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityStorageManager extends SavedData {

    // 存储所有磁盘的Map，键为UUID，值为DataStorage对象
    private final Map<UUID, InfinityDataStorage> cells;


    // 构造方法，初始化磁盘Map
    public InfinityStorageManager() {
        cells = new HashMap<>();
        // 标记数据为“脏”，确保新创建的实例在下次保存时写入磁盘
        this.setDirty();
    }

    // 私有构造方法，用于从已有Map创建StorageManager
    private InfinityStorageManager(Map<UUID, InfinityDataStorage> cells) {
        // 确保使用已加载的数据
        this.cells = cells;
        // 标记数据为“脏”，确保新创建的实例在下次保存时写入磁盘
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        // 将内存中的所有 cell 序列化为一个 ListTag
        ListTag cellList = new ListTag();
        for (Map.Entry<UUID, InfinityDataStorage> entry : cells.entrySet()) {
            CompoundTag cell = new CompoundTag();
            cell.putUUID(InfinityConstants.INFINITY_CELL_UUID, entry.getKey());
            cell.put(InfinityConstants.INFINITY_CELL_DATA, entry.getValue().serializeNBT());
            cellList.add(cell);
        }
        nbt.put(InfinityConstants.INFINITY_CELL_LIST, cellList);
        // 写入当前格式版本号，便于未来迁移与兼容判断
        nbt.putInt(InfinityConstants.FORMAT_VERSION_FIELD, InfinityConstants.FORMAT_VERSION);
        return nbt;
    }

    // 静态方法，从 NBT 数据反序列化创建 StorageManager 实例
    public static InfinityStorageManager readNbt(CompoundTag nbt) {
        // 读取格式版本，缺省视为 1（兼容旧档）
        int version = nbt.contains(InfinityConstants.FORMAT_VERSION_FIELD) ?
                nbt.getInt(InfinityConstants.FORMAT_VERSION_FIELD) :
                1;

        Map<UUID, InfinityDataStorage> cells = new HashMap<>();
        // 从 NBT 中获取磁盘数据列表，指定类型为 CompoundTag（TAG_COMPOUND）
        ListTag cellList = nbt.getList(InfinityConstants.INFINITY_CELL_LIST, CompoundTag.TAG_COMPOUND);
        // 遍历 cellList 中的每个 CompoundTag
        for (int i = 0; i < cellList.size(); i++) {
            // 获取当前索引的 CompoundTag，表示单个磁盘的数据
            CompoundTag cell = cellList.getCompound(i);
            // 从 CompoundTag 中读取 UUID 和 DataStorage 数据，并存入 cells 映射
            cells.put(cell.getUUID(InfinityConstants.INFINITY_CELL_UUID), InfinityDataStorage.loadFromNBT(cell.getCompound(InfinityConstants.INFINITY_CELL_DATA)));
        }
        // 使用加载的 cells 数据创建新的 StorageManager 实例
        return new InfinityStorageManager(cells);
    }

    // 返回当前已加载的所有 UUID 的不可变视图，用于命令或调试用途
    public Set<UUID> getAllLoadedUUIDs() {
        return Collections.unmodifiableSet(cells.keySet());
    }


    // 更新或添加某个 UUID 对应的数据并标记为脏（需要保存）
    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        cells.put(uuid, infinityDataStorage);
        // 标记数据为“脏”，确保修改后的数据会在下次保存时写入磁盘
        setDirty();
    }

    // 删除某个 UUID 的持久化记录并标记为脏
    public void removeCell(UUID uuid) {
        cells.remove(uuid);
        // 标记数据为“脏”，确保移除操作会在下次保存时反映到磁盘
        setDirty();
    }

    // 检查指定 UUID 是否存在于 disks 映射中
    public boolean hasUUID(UUID uuid) {
        // 返回 cells 映射是否包含指定 UUID
        return cells.containsKey(uuid);
    }

    // 获取或创建某个 UUID 对应的数据容器
    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        // 检查 cells 映射中是否不存在指定 UUID
        if (!cells.containsKey(uuid)) {
            updateCell(uuid, new InfinityDataStorage());
        }
        // 返回指定 UUID 对应的 DataStorage 对象
        return cells.get(uuid);
    }

    // 修改指定 UUID 的磁盘数据，包括堆栈键、数量和总项目数
    public void modifyDisk(UUID uuid, ListTag keys, ListTag amounts, BigInteger itemCount) {
        // 获取或创建指定 UUID 的 DataStorage 对象
        InfinityDataStorage cellToModify = getOrCreateCell(uuid);
        if (keys != null && amounts != null) {
            cellToModify.keys = keys;
            cellToModify.amounts = amounts;
        }
        // 更新 DataStorage 的 itemCount 字段
        cellToModify.itemCount = itemCount;
        // 将修改后的 DataStorage 对象更新到 cells 映射
        updateCell(uuid, cellToModify);
    }

    // 静态方法，获取 StorageManager 的单例实例
    public static InfinityStorageManager getInstance(MinecraftServer server) {
        ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
        // 使用 DataStorage 的 computeIfAbsent 方法加载或创建 StorageManager 实例
        // 如果数据存在，则调用 readNbt 加载；否则调用默认构造器创建新实例
        return world.getDataStorage().computeIfAbsent(
                InfinityStorageManager::readNbt,
                InfinityStorageManager::new,
                InfinityConstants.SAVE_FILE_NAME
        );
    }
}
