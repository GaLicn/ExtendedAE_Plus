package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InfinityStorageManager
 * <p>
 * 世界级别的持久化容器，集中管理所有 InfinityBigInteger 存储单元的序列化数据。
 * 功能要点：
 * - 在世界加载时从存档恢复所有 cell 的数据
 * - 提供按 UUID 获取/创建单个 cell 的数据容器
 * - 在世界保存时将内存数据打包为 NBT 写回存档
 */
public class InfinityStorageManager extends SavedData {

    /**
     * SavedData 文件名常量
     */
    public static final String FILE_NAME = "eap_infinity_biginteger_cells";
    /**
     * Per-world instances to avoid cross-world leakage. Keyed by world ResourceKey<Level>.
     */
    private static final Map<ResourceKey<Level>, InfinityStorageManager> INSTANCES = new ConcurrentHashMap<>();
    /**
     * UUID -> 数据 的内存映射
     */
    private final Map<UUID, InfinityDataStorage> cells = new HashMap<>();

    public InfinityStorageManager() {
        setDirty();
    }

    /**
     * 从 NBT 构造：用于在世界加载时从存档恢复数据
     */
    public InfinityStorageManager(CompoundTag nbt) {
        ListTag cellList = nbt.getList("list", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            CompoundTag cell = cellList.getCompound(i);
            cells.put(cell.getUUID("uuid"), InfinityDataStorage.loadFromNBT(cell.getCompound("data")));
        }
        setDirty();
    }

    /**
     * 根据给定的 ServerLevel 获取或创建该世界对应的 SavedData 实例并缓存到 INSTANCE
     */
    public static InfinityStorageManager getForLevel(ServerLevel level) {
        if (level == null) return null;
        ResourceKey<Level> key = level.dimension();
        InfinityStorageManager mgr = INSTANCES.get(key);
        if (mgr == null) {
            mgr = level.getDataStorage().computeIfAbsent(InfinityStorageManager::new, InfinityStorageManager::new, FILE_NAME);
            INSTANCES.put(key, mgr);
        }
        return mgr;
    }

    /**
     * 返回任何现有实例，作为无法访问 ServerLevel 的代码路径的安全回退。
     */
    public static InfinityStorageManager getAnyInstance() {
        return INSTANCES.values().stream().findFirst().orElse(null);
    }

    /**
     * 删除世界的实例（在世界卸载时调用）
     */
    public static void removeForLevel(ServerLevel level) {
        if (level == null) return;
        INSTANCES.remove(level.dimension());
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        // 将内存中的所有 cell 序列化为一个 ListTag
        ListTag cellList = new ListTag();
        for (Map.Entry<UUID, InfinityDataStorage> entry : cells.entrySet()) {
            CompoundTag cell = new CompoundTag();
            cell.putUUID("uuid", entry.getKey());
            cell.put("data", entry.getValue().serializeNBT());
            cellList.add(cell);
        }
        nbt.put("list", cellList);
        return nbt;
    }

    /**
     * 更新或添加某个 UUID 对应的数据并标记为脏（需要保存）
     */
    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        cells.put(uuid, infinityDataStorage);
        setDirty();
    }

    /**
     * 获取或创建某个 UUID 对应的数据容器
     */
    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        if (!cells.containsKey(uuid)) {
            updateCell(uuid, new InfinityDataStorage());
        }
        return cells.get(uuid);
    }

    /**
     * 修改某个 UUID 对应的键与数量列表并保存（新的签名，stackAmounts 为 ListTag 字符串列表）
     */
    public void modifyCell(UUID cellID, ListTag stackKeys, ListTag stackAmounts) {
        InfinityDataStorage cellToModify = getOrCreateCell(cellID);
        if (stackKeys != null && stackAmounts != null) {
            cellToModify.setKeys(stackKeys);
            cellToModify.setAmounts(stackAmounts);
        }
        updateCell(cellID, cellToModify);
    }

    /**
     * 删除某个 UUID 的持久化记录并标记为脏
     */
    public void removeCell(UUID uuid) {
        cells.remove(uuid);
        setDirty();
    }
}
