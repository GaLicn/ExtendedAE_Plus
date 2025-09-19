package com.extendedae_plus.util.storage;

import com.extendedae_plus.ExtendedAEPlus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * InfinityStorageManager
 * <p>
 * 替代之前基于 SavedData 的实现，本类使用手动文件 I/O 在 world 目录下保存 NBT 数据，
 * 以避免依赖 Minecraft 的 SavedData 机制。
 * 数据保持与之前兼容的 NBT 结构：根 Compound 包含 "list" => ListTag of Compound { uuid, data }
 */
public class InfinityStorageManager {

    public static final String FILE_NAME = "eap_infinity_biginteger_cells.dat";

    /**
     * 全局单例，由 mod 在 world load 时初始化
     */
    public static volatile InfinityStorageManager INSTANCE = new InfinityStorageManager();

    private final Map<UUID, InfinityDataStorage> cells = new HashMap<>();

    private Path saveFilePath = null;

    private InfinityStorageManager() {}

    /**
     * 初始化并从 world 保存目录加载数据；若文件不存在则保持空状态
     */
    public void initFromWorld(@Nullable ServerLevel serverLevel) {
        if (serverLevel == null) return;
        try {
            File worldFolder = serverLevel.getServer().getWorldPath(LevelResource.ROOT).toFile();
            // 保存到 world/<modid>/ 文件夹下，避免与其它 mod 冲突
            File modDir = new File(worldFolder, ExtendedAEPlus.MODID);
            if (!modDir.exists()) {
                modDir.mkdirs();
            }
            this.saveFilePath = new File(modDir, FILE_NAME).toPath();
            if (Files.exists(this.saveFilePath)) {
                CompoundTag root = NbtIo.readCompressed(this.saveFilePath.toFile());
                ListTag cellList = root.getList("list", Tag.TAG_COMPOUND);
                for (int i = 0; i < cellList.size(); i++) {
                    CompoundTag cell = cellList.getCompound(i);
                    this.cells.put(cell.getUUID("uuid"), InfinityDataStorage.loadFromNBT(cell.getCompound("data")));
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
        if (this.saveFilePath == null)
            return;
        try {
            CompoundTag root = new CompoundTag();
            ListTag cellList = new ListTag();
            for (Map.Entry<UUID, InfinityDataStorage> entry : this.cells.entrySet()) {
                CompoundTag cell = new CompoundTag();
                cell.putUUID("uuid", entry.getKey());
                cell.put("data", entry.getValue().serializeNBT());
                cellList.add(cell);
            }
            root.put("list", cellList);
            // 使用压缩写入以节省空间
            NbtIo.writeCompressed(root, this.saveFilePath.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateCell(UUID uuid, InfinityDataStorage infinityDataStorage) {
        this.cells.put(uuid, infinityDataStorage);
        saveToFile();
    }

    public InfinityDataStorage getOrCreateCell(UUID uuid) {
        if (!this.cells.containsKey(uuid)) {
            InfinityDataStorage newCell = new InfinityDataStorage();
            this.cells.put(uuid, newCell);
            saveToFile();
        }
        return this.cells.get(uuid);
    }

    public void modifyCell(UUID cellID, ListTag stackKeys, ListTag stackAmounts) {
        InfinityDataStorage cellToModify = getOrCreateCell(cellID);
        if (stackKeys != null && stackAmounts != null) {
            cellToModify.keys = stackKeys;
            cellToModify.amounts = stackAmounts;
        }
        updateCell(cellID, cellToModify);
    }

    public void removeCell(UUID uuid) {
        this.cells.remove(uuid);
        saveToFile();
    }
}
