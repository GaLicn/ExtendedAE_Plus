package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

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

    // 当前磁盘格式版本号，增加字段用于向后/向前兼容
    private static final int FORMAT_VERSION = 1;


    /**
     * SavedData 文件名常量
     */
    public static final String FILE_NAME = "eap_infinity_biginteger_cells";
    /**
     * 全局单例实例（在世界加载时由 InfiniteBigIntegerStorageCell.onLevelLoad 填充）
     */
    public static InfinityStorageManager INSTANCE = null;
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
        // 读取格式版本，缺省视为 1（兼容旧档）
        int version = nbt.contains("format_version") ? nbt.getInt("format_version") : 1;
        if (!nbt.contains("format_version")) {
            // 旧档未包含 format_version，标记为需要写回以便下次保存写入版本号
            LOGGER.info("Found legacy InfinityStorageManager dat without format_version; treating as version {} and marking dirty for upgrade", version);
            // 立即标记为脏以触发尽快写盘（升级写入 format_version）
            this.setDirty();
        } else {
            LOGGER.info("Loading InfinityStorageManager format_version={}", version);
        }

        ListTag cellList = nbt.getList("list", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            CompoundTag cell = cellList.getCompound(i);
            java.util.UUID uuid = cell.getUUID("uuid");
            CompoundTag dataTag = cell.getCompound("data");
            InfinityDataStorage data = InfinityDataStorage.loadFromNBT(dataTag);
            cells.put(uuid, data);
            LOGGER.info("Loaded InfinityDataStorage for uuid {}: keys={}, amounts={}", uuid, data.keys.size(), data.amounts.size());
        }
        setDirty();
    }

    /**
     * 根据给定的 ServerLevel 获取或创建该世界对应的 SavedData 实例并缓存到 INSTANCE
     */
    public static InfinityStorageManager getForLevel(ServerLevel level) {
        if (INSTANCE == null && level != null) {
            // 迁移逻辑：若旧的压缩 dat 文件存在且缺少 format_version，则读取并补上 version，备份原文件
            try {
                Path dataDir = level.getServer().getWorldPath(new LevelResource("data"));
                Path filePath = dataDir.resolve(FILE_NAME + ".dat");
                File oldFile = filePath.toFile();
                if (oldFile.exists()) {
                    // 检测是否为 gzip（压缩 NBT）
                    boolean isGzip = false;
                    try (var fis = Files.newInputStream(filePath)) {
                        int b1 = fis.read();
                        int b2 = fis.read();
                        isGzip = (b1 == 0x1F && b2 == 0x8B);
                    } catch (IOException ignored) {
                    }
                    if (isGzip) {
                        try {
                            var root = NbtIo.readCompressed(oldFile);
                            if (!root.contains("format_version")) {
                                // 备份旧文件
                                Path bak = filePath.resolveSibling(FILE_NAME + ".dat.bak");
                                try {
                                    Files.copy(filePath, bak);
                                } catch (IOException ignored) {
                                }
                                // 写回并加入 format_version
                                root.putInt("format_version", FORMAT_VERSION);
                                Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
                                File tmpFile = tmp.toFile();
                                if (tmpFile.getParentFile() != null && !tmpFile.getParentFile().exists()) {
                                    tmpFile.getParentFile().mkdirs();
                                }
                                NbtIo.writeCompressed(root, tmpFile);
                                try {
                                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                                } catch (AtomicMoveNotSupportedException ex) {
                                    Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
                                }
                                LOGGER.info("Migrated old compressed {} to include format_version", filePath);
                            }
                        } catch (IOException ex) {
                            LOGGER.info("Failed to migrate old compressed file {}: {}", filePath, ex.getMessage());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            INSTANCE = level.getDataStorage().computeIfAbsent(InfinityStorageManager::new, InfinityStorageManager::new, FILE_NAME);
        }
        return INSTANCE;
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
        // 写入当前格式版本号，便于未来迁移与兼容判断
        nbt.putInt("format_version", FORMAT_VERSION);
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
            cellToModify.keys = stackKeys;
            cellToModify.amounts = stackAmounts;
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
