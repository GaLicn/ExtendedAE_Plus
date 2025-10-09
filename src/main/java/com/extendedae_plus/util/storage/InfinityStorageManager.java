package com.extendedae_plus.util.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * MIT License
 *
 * Copyright (c) 2025 Frostbite-time
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */

/**
 * 无尽元件的SavedData管理类
 * @author Frostbite
 */
public class InfinityStorageManager extends SavedData {
    private static final Factory<InfinityStorageManager> FACTORY = new Factory<>(InfinityStorageManager::new, InfinityStorageManager::readNbt);
    // 存储所有磁盘的Map，键为UUID，值为DataStorage对象
    private final Map<UUID, InfinityDataStorage> cells;

    // 构造方法，初始化磁盘Map
    public InfinityStorageManager() {
        cells = new HashMap<>();
    }

    // 私有构造方法，用于从已有Map创建StorageManager
    private InfinityStorageManager(Map<UUID, InfinityDataStorage> cells) {
        // 确保使用已加载的数据
        this.cells = cells;
    }


    // 静态方法，从 NBT 数据反序列化创建 StorageManager 实例
    public static InfinityStorageManager readNbt(CompoundTag nbt, HolderLookup.Provider registries) {
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
            cells.put(cell.getUUID(InfinityConstants.INFINITY_CELL_UUID), InfinityDataStorage.loadFromNBT(cell.getCompound(InfinityConstants.INFINITY_CELL_DATA), registries));
        }
        // 使用加载的 cells 数据创建新的 StorageManager 实例
        return new InfinityStorageManager(cells);
    }


    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider provider) {
        // 将内存中的所有 cell 序列化为一个 ListTag
        ListTag cellList = new ListTag();
        for (Map.Entry<UUID, InfinityDataStorage> entry : cells.entrySet()) {
            CompoundTag cell = new CompoundTag();
            cell.putUUID(InfinityConstants.INFINITY_CELL_UUID, entry.getKey());
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
            InfinityDataStorage cell = new InfinityDataStorage();
            cells.put(uuid, cell);
            return cell;
        }
        // 返回指定 UUID 对应的 DataStorage 对象
        return cells.get(uuid);
    }

    public static @Nullable InfinityStorageManager getInstance() {
        // 用neo提供的钩子拿服务端，再从服务端拿信息，注册表信息也无需在这里拿取，getCurrentServer始终会返回对当前来说正确的服务端
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, InfinityConstants.SAVE_FILE_NAME);
    }
}
