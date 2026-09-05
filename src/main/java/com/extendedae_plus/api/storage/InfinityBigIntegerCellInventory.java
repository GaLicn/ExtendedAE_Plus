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
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.UUID;

public class InfinityBigIntegerCellInventory implements StorageCell {
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger BI_THOUSAND = BigInteger.valueOf(1_000);
    private static final BigDecimal DECIMAL_THOUSAND = BigDecimal.valueOf(1_000);
    private static final String[] BIG_INTEGER_UNITS = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};

    private final InfinityBigIntegerCellItem cell;
    // 磁盘本身
    private final ItemStack self;
    // UUID 在库存对象生命周期内不变，避免高频存取时复制 CustomData 的 NBT
    @Nullable
    private UUID cellUuid;
    @Nullable
    private final InfinityStorageManager storageManager;
    @Nullable
    private InfinityDataStorage cachedCellStorage;
    private long cachedStorageRevision = Long.MIN_VALUE;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final boolean hasPartitionFilter;
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
        this.hasPartitionFilter = !this.partitionList.isEmpty();
    }

    // 将 BigInteger 格式化为带单位的字符串，保留两位小数
    public static String formatBigInteger(BigInteger number) {
        if (number.compareTo(BI_THOUSAND) < 0) {
            return number.toString();
        }

        DecimalFormat df = new DecimalFormat("#.##");
        BigDecimal bd = new BigDecimal(number);
        BigDecimal thousand = DECIMAL_THOUSAND;
        String[] units = BIG_INTEGER_UNITS;
        int idx = 0;
        while (bd.compareTo(thousand) >= 0 && idx < units.length - 1) {
            bd = bd.divide(thousand, 2, RoundingMode.HALF_UP);
            idx++;
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
        UUID uuid = this.cellUuid;
        if (uuid == null || this.storageManager == null) {
            return null;
        }

        long storageRevision = this.storageManager.getStorageRevision();
        if (this.cachedStorageRevision != storageRevision) {
            this.cachedCellStorage = this.storageManager.getCell(uuid);
            this.cachedStorageRevision = storageRevision;
        }
        return this.cachedCellStorage;
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

        InfinityDataStorage cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            cellStorage = this.storageManager.getOrCreateCell(uuid);
            this.cachedCellStorage = cellStorage;
            this.cachedStorageRevision = this.storageManager.getStorageRevision();
        }
        return cellStorage;
    }

    @Override
    public CellState getStatus() {
        InfinityDataStorage cellStorage = this.getExistingCellStorage();
        return cellStorage != null && cellStorage.hasItems()
                ? CellState.NOT_EMPTY
                : CellState.EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 512;
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        InfinityDataStorage cellStorage = this.getExistingCellStorage();
        BigInteger totalAmount = cellStorage == null ? BigInteger.ZERO : cellStorage.getItemCount();
        int itemTypes = cellStorage == null ? 0 : cellStorage.size();

        CompoundTag tag = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (totalAmount.signum() <= 0) {
            tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
            tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
            // 向后兼容
            tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        } else {
            byte[] itemCountBytes = totalAmount.toByteArray();
            tag.putByteArray(InfinityConstants.INFINITY_ITEM_TOTAL, itemCountBytes);
            tag.putInt(InfinityConstants.INFINITY_ITEM_TYPES, itemTypes);
            tag.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, itemCountBytes);
        }

        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.isPersisted = true;
    }

    private void clearCellData() {
        UUID uuid = this.getUUID();
        if (uuid != null && this.storageManager != null) {
            this.storageManager.removeCell(uuid);
        }

        CompoundTag tag = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(InfinityConstants.INFINITY_CELL_UUID);
        tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
        tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
        // 向后兼容
        tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.cellUuid = null;
        this.cachedCellStorage = null;
        this.cachedStorageRevision = Long.MIN_VALUE;
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
        tag.putUUID(InfinityConstants.INFINITY_CELL_UUID, newUUID);
        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.cellUuid = newUUID;
        return newUUID;
    }

    @Nullable
    private UUID readUUIDFromStack() {
        CustomData data = this.self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            return null;
        }

        CompoundTag tag = data.copyTag();
        return tag.contains(InfinityConstants.INFINITY_CELL_UUID)
                ? tag.getUUID(InfinityConstants.INFINITY_CELL_UUID)
                : null;
    }

    public UUID getUUID() {
        return this.cellUuid;
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
        if (amount <= 0) {
            return 0;
        }
        if (this.storageManager == null) {
            return 0;
        }
        if (this.hasPartitionFilter && !this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }
        if (what instanceof AEItemKey itemKey &&
                itemKey.getItem() instanceof InfinityBigIntegerCellItem &&
                itemKey.get(DataComponents.CUSTOM_DATA) != null) {
            return 0;
        }

        // 模拟时不分配UUID或创建SavedData条目。此路径在AE2规划网络操作时被频繁使用。
        if (mode != Actionable.MODULATE) {
            return amount;
        }

        var cellStorage = this.getWritableCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        cellStorage.insert(what, amount);
        this.saveChanges();

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || this.storageManager == null) {
            return 0;
        }

        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        if (mode != Actionable.MODULATE) {
            return cellStorage.getExtractableAmount(what, amount);
        }

        long extractedAmount = cellStorage.extract(what, amount, true);
        if (extractedAmount > 0) {
            if (cellStorage.size() == 0) {
                this.clearCellData();
            } else {
                this.saveChanges();
            }
        }

        return extractedAmount;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        InfinityDataStorage cellStorage = this.getExistingCellStorage();
        if (cellStorage == null || !cellStorage.hasItems()) {
            return;
        }

        // 网络聚合器通常从空 KeyCounter 开始；本磁盘内每个 key 唯一时无需逐项查询旧值。
        boolean outputWasEmpty = out.isEmpty();
        for (var entry : Object2LongMaps.fastIterable(cellStorage.longAmounts)) {
            AEKey key = entry.getKey();
            long value = entry.getLongValue();
            if (value <= 0) {
                continue;
            }

            if (outputWasEmpty) {
                out.add(key, value);
                continue;
            }

            long existing = out.get(key);
            if (existing == Long.MAX_VALUE) {
                continue;
            }

            if (existing > Long.MAX_VALUE - value) {
                out.set(key, Long.MAX_VALUE);
            } else {
                out.add(key, value);
            }
        }

        for (var entry : Object2ObjectMaps.fastIterable(cellStorage.bigAmounts)) {
            AEKey key = entry.getKey();
            BigInteger value = entry.getValue();
            if (value == null || value.signum() <= 0) {
                continue;
            }

            if (outputWasEmpty) {
                out.set(key, Long.MAX_VALUE);
                continue;
            }

            long existing = out.get(key);
            if (existing == Long.MAX_VALUE) {
                continue;
            }

            // 极大数 Map 中的数量必定超过 Long.MAX_VALUE，正常统计可直接饱和。
            if (existing >= 0) {
                out.set(key, Long.MAX_VALUE);
            } else {
                // KeyCounter 允许负数，只有这个罕见边界需要进行 BigInteger 加法。
                BigInteger sum = value.add(BigInteger.valueOf(existing));
                out.set(key, sum.compareTo(BI_LONG_MAX) > 0 ? Long.MAX_VALUE : sum.longValue());
            }
        }
    }

    @Override
    public Component getDescription() {
        return this.self.getHoverName();
    }
}
