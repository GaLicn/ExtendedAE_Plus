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
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    // 存储的物品种类数量
    private int totalAEKeyType;
    // 存储的物品总数
    private BigInteger totalAEKey2Amounts = BI_ZERO;
    // 仅用于控制 ItemStack 摘要字段是否需要刷新
    private boolean isPersisted = true;

    private static final BigInteger BI_ZERO = BigInteger.ZERO;
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    public InfinityBigIntegerCellInventory(InfinityBigIntegerCellItem cell, ItemStack stack, ISaveProvider saveProvider) {
        this.cell = cell;
        this.self = stack;
        this.container = saveProvider;

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

    @Override
    public Component getDescription() {
        return self.getHoverName();
    }

    public static InfinityBigIntegerCellInventory createInventory(ItemStack stack, ISaveProvider saveProvider) {
        Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
        if (!(stack.getItem() instanceof InfinityBigIntegerCellItem cell)) {
            return null;
        }
        return new InfinityBigIntegerCellInventory(cell, stack, saveProvider);
    }

    @Override
    public CellState getStatus() {
        this.refreshCachedStateFromStorage();
        if (this.getTotalAEKey2Amounts().equals(BI_ZERO)) {
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

        CompoundTag tag = self.getOrCreateTag();
        if (this.totalAEKey2Amounts.equals(BI_ZERO)) {
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

        this.isPersisted = true;
    }

    public BigInteger getTotalAEKey2Amounts() {
        this.refreshCachedStateFromStorage();
        return this.totalAEKey2Amounts;
    }

    public int getTotalAEKeyType() {
        this.refreshCachedStateFromStorage();
        return this.totalAEKeyType;
    }

    public boolean hasUUID() {
        return self.hasTag() && self.getOrCreateTag().contains(InfinityConstants.INFINITY_CELL_UUID);
    }

    public UUID getUUID() {
        if (this.hasUUID()) {
            return self.getOrCreateTag().getUUID(InfinityConstants.INFINITY_CELL_UUID);
        }
        return null;
    }

    private void refreshCachedStateFromStorage() {
        var cellStorage = this.getExistingCellStorage();
        if (cellStorage != null) {
            this.totalAEKeyType = cellStorage.amounts.size();
            this.totalAEKey2Amounts = cellStorage.itemCount == null ? BI_ZERO : cellStorage.itemCount;
        } else {
            this.totalAEKeyType = 0;
            this.totalAEKey2Amounts = BI_ZERO;
        }
    }

    private void initData() {
        this.refreshCachedStateFromStorage();
    }

    private Object2ObjectMap<AEKey, BigInteger> getCellStoredMap() {
        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return Object2ObjectMaps.emptyMap();
        }
        return cellStorage.amounts;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : this.getCellStoredMap().object2ObjectEntrySet()) {
            AEKey key = entry.getKey();
            BigInteger amount = entry.getValue();

            if (amount.compareTo(BI_LONG_MAX) > 0) {
                out.set(key, Long.MAX_VALUE);
                continue;
            }

            long existing = out.get(key);
            if (existing == Long.MAX_VALUE) {
                continue;
            }

            long addAmount = amount.longValue();
            long sum = existing + addAmount;
            if (sum < 0 || sum < existing) {
                out.set(key, Long.MAX_VALUE);
            } else if (addAmount != 0) {
                out.add(key, addAmount);
            }
        }
    }

    private InfinityStorageManager getStorageManagerInstance() {
        return ExtendedAEPlus.STORAGE_INSTANCE;
    }

    private InfinityDataStorage getExistingCellStorage() {
        UUID uuid = this.getUUID();
        InfinityStorageManager storageManager = this.getStorageManagerInstance();
        if (uuid == null || storageManager == null || !storageManager.hasUUID(uuid)) {
            return null;
        }
        return storageManager.getOrCreateCell(uuid);
    }

    private InfinityDataStorage getWritableCellStorage() {
        InfinityStorageManager storageManager = this.getStorageManagerInstance();
        if (storageManager == null) {
            return null;
        }

        UUID uuid = this.getUUID();
        if (uuid == null) {
            uuid = this.assignNewUUID();
        }
        return storageManager.getOrCreateCell(uuid);
    }

    private UUID assignNewUUID() {
        CompoundTag tag = self.getOrCreateTag();
        UUID newUUID = UUID.randomUUID();
        tag.putUUID(InfinityConstants.INFINITY_CELL_UUID, newUUID);
        return newUUID;
    }

    private void clearCellData() {
        UUID uuid = this.getUUID();
        InfinityStorageManager storageManager = this.getStorageManagerInstance();
        if (uuid != null && storageManager != null && storageManager.hasUUID(uuid)) {
            storageManager.removeCell(uuid);
        }

        this.totalAEKeyType = 0;
        this.totalAEKey2Amounts = BI_ZERO;

        CompoundTag tag = self.getOrCreateTag();
        tag.remove(InfinityConstants.INFINITY_CELL_UUID);
        tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
        tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
        // backward compat
        tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        this.isPersisted = true;

        if (this.container != null) {
            this.container.saveChanges();
        }
    }

    private void saveChanges() {
        this.isPersisted = false;
        InfinityStorageManager storageManager = this.getStorageManagerInstance();
        if (storageManager != null) {
            storageManager.setDirty();
        }

        if (this.container != null) {
            this.container.saveChanges();
        } else {
            this.persist();
        }
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
        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }
        if (what instanceof AEItemKey itemKey &&
                itemKey.getItem() instanceof InfinityBigIntegerCellItem &&
                itemKey.hasTag()) {
            return 0;
        }

        var cellStorage = this.getWritableCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        BigInteger currentAmount = cellStorage.amounts.getOrDefault(what, BI_ZERO);
        if (mode == Actionable.MODULATE) {
            BigInteger delta = BigInteger.valueOf(amount);
            if (currentAmount.equals(BI_ZERO)) {
                this.totalAEKeyType++;
            }

            BigInteger newAmount = currentAmount.add(delta);
            cellStorage.amounts.put(what, newAmount);
            this.totalAEKey2Amounts = this.totalAEKey2Amounts.add(delta);
            cellStorage.itemCount = this.totalAEKey2Amounts;
            this.saveChanges();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        BigInteger currentAmount = cellStorage.amounts.getOrDefault(what, BI_ZERO);
        if (currentAmount.compareTo(BI_ZERO) <= 0) {
            return 0;
        }

        BigInteger requested = BigInteger.valueOf(amount);
        if (requested.compareTo(currentAmount) >= 0) {
            if (mode == Actionable.MODULATE) {
                cellStorage.amounts.remove(what);
                this.totalAEKeyType--;
                this.totalAEKey2Amounts = this.totalAEKey2Amounts.subtract(currentAmount);
                cellStorage.itemCount = this.totalAEKey2Amounts;

                if (cellStorage.amounts.isEmpty()) {
                    this.clearCellData();
                } else {
                    this.saveChanges();
                }
            }
            return currentAmount.compareTo(BI_LONG_MAX) > 0 ? Long.MAX_VALUE : currentAmount.longValue();
        }

        if (mode == Actionable.MODULATE) {
            BigInteger newAmount = currentAmount.subtract(requested);
            cellStorage.amounts.put(what, newAmount);
            this.totalAEKey2Amounts = this.totalAEKey2Amounts.subtract(requested);
            cellStorage.itemCount = this.totalAEKey2Amounts;
            this.saveChanges();
        }
        return requested.longValue();
    }

    public String getTotalStorage() {
        this.refreshCachedStateFromStorage();
        return formatBigInteger(totalAEKey2Amounts);
    }
}
