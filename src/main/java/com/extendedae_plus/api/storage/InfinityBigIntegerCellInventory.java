package com.extendedae_plus.api.storage;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityBigIntegerCellInventory implements StorageCell {
    private final InfinityBigIntegerCellItem cell;
    // 磁盘本身
    private final ItemStack self;
    // UUID 在库存对象生命周期内不变，避免高频存取时复制 CustomData 的 NBT
    @Nullable
    private UUID cellUuid;
    @Nullable
    private final InfinityStorageManager storageManager;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    // 存储的物品种类数量
    private int totalAEKeyType;
    // 存储的物品总数
    private BigInteger totalAEKey2Amounts = BigInteger.ZERO;
    // 仅用于控制 ItemStack 摘要字段是否需要刷新
    private boolean isPersisted = true;

    private InfinityBigIntegerCellInventory(InfinityBigIntegerCellItem cell,
                                            ItemStack stack,
                                            ISaveProvider saveProvider,
                                            @Nullable InfinityStorageManager storageManager) {
        this.cell = cell;
        this.self = stack;
        this.cellUuid = this.readUUIDFromStack();
        this.container = saveProvider;
        this.storageManager = storageManager;

        var builder = IPartitionList.builder();
        var upgrades = this.getUpgradesInventory();
        var config = this.getConfigInventory();
        boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
        boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        if (isFuzzy) {
            builder.fuzzyMode(this.getFuzzyMode());
        }
        builder.addAll(config.keySet());
        this.partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.partitionList = builder.build();
        this.initData();
    }

    // 将 BigInteger 格式化为带单位的字符串，保留两位小数
    public static String formatBigInteger(BigInteger number) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");
        BigDecimal bd = new BigDecimal(number);
        BigDecimal thousand = new BigDecimal(1000);
        String[] units = new String[]{"", "K", "M", "G", "T", "P", "E", "Z", "Y"};
        int idx = 0;
        while (bd.compareTo(thousand) >= 0 && idx < units.length - 1) {
            bd = bd.divide(thousand, 2, RoundingMode.HALF_UP);
            idx++;
        }
        if (idx == 0) {
            return bd.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        return df.format(bd.doubleValue()) + units[idx];
    }

    static InfinityBigIntegerCellInventory createInventory(ItemStack stack,
                                                           ISaveProvider saveProvider,
                                                           @Nullable InfinityStorageManager storageManager) {
        Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
        if (!(stack.getItem() instanceof InfinityBigIntegerCellItem cell)) {
            return null;
        }
        return new InfinityBigIntegerCellInventory(cell, stack, saveProvider, storageManager);
    }

    @Nullable
    private InfinityDataStorage getExistingCellStorage() {
        UUID uuid = this.getUUID();
        if (uuid == null || this.storageManager == null || !this.storageManager.hasUUID(uuid)) {
            return null;
        }
        return this.storageManager.getOrCreateCell(uuid);
    }

    @Nullable
    private InfinityDataStorage getWritableCellStorage() {
        if (this.storageManager == null) {
            return null;
        }

        UUID uuid = this.getUUID();
        if (uuid == null) {
            uuid = this.assignNewUUID();
        }
        return this.storageManager.getOrCreateCell(uuid);
    }

    private void initData() {
        this.refreshCachedStateFromStorage();
    }

    private void refreshCachedStateFromStorage() {
        var cellStorage = this.getExistingCellStorage();
        if (cellStorage != null) {
            this.totalAEKeyType = cellStorage.size();
            this.totalAEKey2Amounts = cellStorage.getItemCount();
        } else {
            this.totalAEKeyType = 0;
            this.totalAEKey2Amounts = BigInteger.ZERO;
        }
    }

    @Override
    public CellState getStatus() {
        this.refreshCachedStateFromStorage();
        if (this.getTotalAEKey2Amounts().equals(BigInteger.ZERO)) {
            return CellState.EMPTY;
        }
        return CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 512;
    }

    @Override
    public void persist() {
        this.refreshCachedStateFromStorage();
        if (this.isPersisted) {
            return;
        }

        CompoundTag tag = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (this.totalAEKey2Amounts.equals(BigInteger.ZERO)) {
            tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
            tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
            // backward compat
            tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        } else {
            byte[] itemCountBytes = this.totalAEKey2Amounts.toByteArray();
            tag.putByteArray(InfinityConstants.INFINITY_ITEM_TOTAL, itemCountBytes);
            tag.putInt(InfinityConstants.INFINITY_ITEM_TYPES, this.totalAEKeyType);
            tag.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, itemCountBytes);
        }

        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.isPersisted = true;
    }

    private void clearCellData() {
        UUID uuid = this.getUUID();
        if (uuid != null && this.storageManager != null && this.storageManager.hasUUID(uuid)) {
            this.storageManager.removeCell(uuid);
        }

        this.totalAEKeyType = 0;
        this.totalAEKey2Amounts = BigInteger.ZERO;

        CompoundTag tag = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(InfinityConstants.INFINITY_CELL_UUID);
        tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
        tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
        // backward compat
        tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.cellUuid = null;
        this.isPersisted = true;

        if (this.container != null) {
            this.container.saveChanges();
        }
    }

    private void saveChanges() {
        this.isPersisted = false;
        if (this.storageManager != null) {
            this.storageManager.setDirty();
        }

        if (this.container != null) {
            this.container.saveChanges();
        } else {
            this.persist();
        }
    }

    private UUID assignNewUUID() {
        CustomData data = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        UUID newUUID = UUID.randomUUID();
        tag.store(InfinityConstants.INFINITY_CELL_UUID, UUIDUtil.CODEC, newUUID);
        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.cellUuid = newUUID;
        return newUUID;
    }

    // 获取存储的物品总数
    private BigInteger getTotalAEKey2Amounts() {
        return this.totalAEKey2Amounts;
    }

    public int getTotalAEKeyType() {
        this.refreshCachedStateFromStorage();
        return this.totalAEKeyType;
    }

    private boolean hasUUID() {
        return this.cellUuid != null;
    }

    @Nullable
    private UUID readUUIDFromStack() {
        CustomData data = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            return null;
        }

        CompoundTag tag = data.copyTag();
        return tag.contains(InfinityConstants.INFINITY_CELL_UUID)
                ? tag.read(InfinityConstants.INFINITY_CELL_UUID, UUIDUtil.CODEC).orElse(null)
                : null;
    }

    public UUID getUUID() {
        return this.cellUuid;
    }

    private InfinityDataStorage getCellStoredStorage() {
        var cellStorage = this.getExistingCellStorage();
        return cellStorage == null ? InfinityDataStorage.EMPTY : cellStorage;
    }

    private ConfigInventory getConfigInventory() {
        return this.cell.getConfigInventory(this.self);
    }

    private IUpgradeInventory getUpgradesInventory() {
        return this.cell.getUpgrades(this.self);
    }

    private FuzzyMode getFuzzyMode() {
        return this.cell.getFuzzyMode(this.self);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount == 0) {
            return 0;
        }
        if (this.storageManager == null) {
            return 0;
        }
        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }
        if (what instanceof AEItemKey itemKey &&
                itemKey.getItem() instanceof InfinityBigIntegerCellItem &&
                itemKey.get(DataComponents.CUSTOM_DATA) != null) {
            return 0;
        }

        var cellStorage = this.getWritableCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            if (cellStorage.getExtractableAmount(what, 1) == 0) this.totalAEKeyType++;
            cellStorage.insert(what, amount);
            this.totalAEKey2Amounts = cellStorage.getItemCount();
            this.saveChanges();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (this.storageManager == null) {
            return 0;
        }

        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        long extracted = cellStorage.getExtractableAmount(what, amount);
        if (extracted <= 0) return 0;
        if (mode == Actionable.MODULATE) {
            cellStorage.extract(what, extracted, true);
            this.totalAEKey2Amounts = cellStorage.getItemCount();
            if (!cellStorage.hasItems()) {
                this.clearCellData();
            } else {
                this.totalAEKeyType = cellStorage.size();
                this.saveChanges();
            }
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var storage = this.getCellStoredStorage();
        for (var entry : storage.longAmounts.object2LongEntrySet()) {
            long existing = out.get(entry.getKey());
            if (existing != Long.MAX_VALUE) out.add(entry.getKey(), Math.min(entry.getLongValue(), Long.MAX_VALUE - existing));
        }
        for (var entry : storage.bigAmounts.object2ObjectEntrySet()) {
            long existing = out.get(entry.getKey());
            if (existing != Long.MAX_VALUE) out.add(entry.getKey(), Long.MAX_VALUE - existing);
        }
    }

    @Override
    public Component getDescription() {
        return this.self.getHoverName();
    }

    public String getTotalStorage() {
        this.refreshCachedStateFromStorage();
        return formatBigInteger(this.totalAEKey2Amounts);
    }
}
