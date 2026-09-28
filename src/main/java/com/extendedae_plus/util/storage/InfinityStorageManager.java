package com.extendedae_plus.util.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityStorageManager extends SavedData {
    private static final SavedDataType<InfinityStorageManager> TYPE = new SavedDataType<>(
            Identifier.parse(InfinityConstants.SAVE_FILE_NAME),
            InfinityStorageManager::new,
            CompoundTag.CODEC.xmap(tag -> readNbt(tag, null), data -> data.save(new CompoundTag(), null)),
            null);
    // 存储所有磁盘的Map，键为UUID，值为DataStorage对象
    private final Object2ObjectMap<UUID, InfinityDataStorage> cells;
    private long storageRevision;
    @Nullable
    private WeakReference<HolderLookup.Provider> registries;


    // 构造方法，初始化磁盘Map
    public InfinityStorageManager() {
        cells = new Object2ObjectOpenHashMap<>();
        // 标记数据为“脏”，确保新创建的实例在下次保存时写入磁盘
        this.setDirty();
    }

    // 私有构造方法，用于从已有Map创建StorageManager
    private InfinityStorageManager(Object2ObjectMap<UUID, InfinityDataStorage> cells) {
        // 确保使用已加载的数据
        this.cells = cells;
        // 标记数据为“脏”，确保新创建的实例在下次保存时写入磁盘
        this.setDirty();
    }


    // 静态方法，从 NBT 数据反序列化创建 StorageManager 实例
    public static InfinityStorageManager readNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        // 读取格式版本，缺省视为 1（兼容旧档）
        int version = nbt.contains(InfinityConstants.FORMAT_VERSION_FIELD) ?
                nbt.getInt(InfinityConstants.FORMAT_VERSION_FIELD).orElse(1) :
                1;

        ListTag cellList = nbt.getList(InfinityConstants.INFINITY_CELL_LIST).orElse(new ListTag());
        Object2ObjectMap<UUID, InfinityDataStorage> cells = new Object2ObjectOpenHashMap<>(Math.max(2, cellList.size()));
        // 遍历 cellList 中的每个 CompoundTag
        for (int i = 0; i < cellList.size(); i++) {
            // 获取当前索引的 CompoundTag，表示单个磁盘的数据
            CompoundTag cell = cellList.getCompound(i).orElseThrow();
            // 从 CompoundTag 中读取 UUID 和 DataStorage 数据，并存入 cells 映射
            cells.put(
                    cell.read(InfinityConstants.INFINITY_CELL_UUID, UUIDUtil.CODEC).orElseThrow(),
                    InfinityDataStorage.loadFromNBT(cell.getCompound(InfinityConstants.INFINITY_CELL_DATA).orElseThrow(), registries)
            );
        }
        // 使用加载的 cells 数据创建新的 StorageManager 实例
        return new InfinityStorageManager(cells);
    }


    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        // 将内存中的所有 cell 序列化为一个 ListTag
        ListTag cellList = new ListTag();
        for (var entry : Object2ObjectMaps.fastIterable(cells)) {
            CompoundTag cell = new CompoundTag();
            cell.store(InfinityConstants.INFINITY_CELL_UUID, UUIDUtil.CODEC, entry.getKey());
            cell.put(InfinityConstants.INFINITY_CELL_DATA, entry.getValue().serializeNBT(provider));
            cellList.add(cell);
        }
        nbt.put(InfinityConstants.INFINITY_CELL_LIST, cellList);
        // 写入当前格式版本号，便于未来迁移与兼容判断
        nbt.putInt(InfinityConstants.FORMAT_VERSION_FIELD, InfinityConstants.FORMAT_VERSION);
        return nbt;
    }

    // 返回当前已加载的所有 UUID 的不可变视图，用于命令或调试用途
    public Set<UUID> getAllLoadedUUIDs() {
        return Collections.unmodifiableSet(cells.keySet());
    }

    // 更新或添加某个 UUID 对应的数据并标记为脏（需要保存）
    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        if (cells.put(uuid, infinityDataStorage) != infinityDataStorage) storageRevision++;
        setDirty();
    }

    // 删除某个 UUID 的持久化记录并标记为脏
    public void removeCell(UUID uuid) {
        if (cells.remove(uuid) != null) {
            storageRevision++;
            setDirty();
        }
    }

    // 检查指定 UUID 是否存在于 disks 映射中
    public boolean hasUUID(UUID uuid) {
        // 返回 cells 映射是否包含指定 UUID
        return cells.containsKey(uuid);
    }

    // 获取或创建某个 UUID 对应的数据容器
    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        InfinityDataStorage cell = cells.get(uuid);
        if (cell == null) {
            cell = new InfinityDataStorage();
            cells.put(uuid, cell);
            storageRevision++;
            setDirty();
        }
        return cell;
    }

    @Nullable
    public InfinityDataStorage getCell(UUID uuid) {
        return cells.get(uuid);
    }

    public long getStorageRevision() {
        return storageRevision;
    }


    public static InfinityStorageManager getInstance(MinecraftServer server) {
        ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
        var manager = world.getDataStorage().computeIfAbsent(TYPE);
        manager.registries = new WeakReference<>(server.registryAccess());
        return manager;
    }

    public HolderLookup.Provider getRegistries() {
        var r = this.registries;
        if (r == null) {
            throw new IllegalStateException("StorageManager was not initialized properly.");
        }

        var registries = r.get();
        if (registries == null) {
            throw new IllegalStateException("Using a StorageManager whose server was already closed");
        }

        return registries;
    }
}
