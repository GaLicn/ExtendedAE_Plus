package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * InfinityStorageManager
 *
 * 替代之前基于 SavedData 的实现，本类使用手动文件 I/O 在 world 目录下保存 NBT 数据，
 * 以避免依赖 Minecraft 的 SavedData 机制。
 * 数据保持与之前兼容的 NBT 结构：根 Compound 包含 "list" => ListTag of Compound { uuid, data }
 */
public class InfinityStorageManager {

    public static final String FILE_NAME = "eap_infinity_biginteger_cells.dat";

    /** 全局单例，由 mod 在 world load 时初始化 */
    public static volatile InfinityStorageManager INSTANCE = new InfinityStorageManager();

    private final Map<UUID, InfinityDataStorage> cells = new HashMap<>();

    private Path saveFilePath = null;

    public InfinityStorageManager() {
    }

    /**
     * 初始化并从 world 保存目录加载数据；若文件不存在则保持空状态
     */
    public void initFromWorld(@Nullable ServerLevel serverLevel) {
        if (serverLevel == null) return;
        try {
            File worldFolder = serverLevel.getServer().getWorldPath(LevelResource.ROOT).toFile();
            // 保存到 world/<modid>/ 文件夹下，避免与其它 mod 冲突
            File modDir = new File(worldFolder, "data");
            if (!modDir.exists()) modDir.mkdirs();
            saveFilePath = new File(modDir, FILE_NAME).toPath();
            if (Files.exists(saveFilePath)) {
                CompoundTag root = NbtIo.readCompressed(saveFilePath.toFile());
                ListTag cellList = root.getList("list", Tag.TAG_COMPOUND);
                for (int i = 0; i < cellList.size(); i++) {
                    CompoundTag cell = cellList.getCompound(i);
                    cells.put(cell.getUUID("uuid"), InfinityDataStorage.loadFromNBT(cell.getCompound("data")));
                }
            }
        } catch (IOException e) {
            // 读取失败保持空，并打印栈追踪以便调试
            e.printStackTrace();
        }
    }

    /**
     * 保存当前内存数据到文件（会覆盖已有文件）
     */
    public synchronized void saveToFile() {
        if (saveFilePath == null) return;
        try {
            CompoundTag root = new CompoundTag();
            ListTag cellList = new ListTag();
            for (Map.Entry<UUID, InfinityDataStorage> entry : cells.entrySet()) {
                // 跳过可能的 null key，防止写入时 NPE
                if (entry.getKey() == null || entry.getValue() == null) continue;
                CompoundTag cell = new CompoundTag();
                cell.putUUID("uuid", entry.getKey());
                cell.put("data", entry.getValue().serializeNBT());
                cellList.add(cell);
            }
            root.put("list", cellList);
            // 使用压缩写入到临时文件，然后原子替换目标文件以避免半成品/0字节文件
            Path tmp = saveFilePath.resolveSibling(FILE_NAME + ".tmp");
            File tmpFile = tmp.toFile();
            // 确保临时文件的目录存在
            if (tmpFile.getParentFile() != null && !tmpFile.getParentFile().exists()) {
                tmpFile.getParentFile().mkdirs();
            }
            NbtIo.writeCompressed(root, tmpFile);
            try {
                Files.move(tmp, saveFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                // 若底层文件系统不支持原子移动，退回到非原子替换
                Files.move(tmp, saveFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        if (uuid == null) return; // 忽略无效 UUID
        cells.put(uuid, infinityDataStorage);
        saveToFile();
    }

    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        if (uuid == null) {
            return InfinityDataStorage.EMPTY;
        }
        if (!cells.containsKey(uuid)) {
            InfinityDataStorage newCell = new InfinityDataStorage();
            cells.put(uuid, newCell);
            saveToFile();
        }
        return cells.get(uuid);
    }

    public void modifyCell(UUID cellID, ListTag stackKeys, ListTag stackAmounts) {
        if (cellID == null) return;
        InfinityDataStorage cellToModify = getOrCreateCell(cellID);
        if (stackKeys != null && stackAmounts != null) {
            cellToModify.keys = stackKeys;
            cellToModify.amounts = stackAmounts;
        }
        updateCell(cellID, cellToModify);
    }

    public void removeCell(UUID uuid) {
        if (uuid == null) return;
        cells.remove(uuid);
        saveToFile();
    }
}
