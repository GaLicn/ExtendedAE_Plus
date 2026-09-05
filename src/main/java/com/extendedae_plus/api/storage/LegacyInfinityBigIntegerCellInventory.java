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
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Benchmark-only source-equivalent snapshot of the cell implementation before
 * commit c8274318. Production code must use InfinityBigIntegerCellInventory.
 */
final class LegacyInfinityBigIntegerCellInventory implements StorageCell {
    private final InfinityBigIntegerCellItem cell;
    private final ItemStack self;
    @Nullable
    private UUID cellUuid;
    @Nullable
    private final LegacyInfinityStorageManager storageManager;
    private final ISaveProvider container;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private int totalAEKeyType;
    private BigInteger totalAEKey2Amounts = BigInteger.ZERO;
    private boolean isPersisted = true;

    private LegacyInfinityBigIntegerCellInventory(InfinityBigIntegerCellItem cell,
                                                  ItemStack stack,
                                                  ISaveProvider saveProvider,
                                                  @Nullable LegacyInfinityStorageManager storageManager) {
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

    static LegacyInfinityBigIntegerCellInventory createInventory(ItemStack stack,
                                                                 ISaveProvider saveProvider,
                                                                 @Nullable LegacyInfinityStorageManager storageManager) {
        Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
        if (!(stack.getItem() instanceof InfinityBigIntegerCellItem cell)) {
            return null;
        }
        return new LegacyInfinityBigIntegerCellInventory(cell, stack, saveProvider, storageManager);
    }

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

    @Nullable
    private LegacyInfinityDataStorage getExistingCellStorage() {
        UUID uuid = this.getUUID();
        if (uuid == null || this.storageManager == null || !this.storageManager.hasUUID(uuid)) {
            return null;
        }
        return this.storageManager.getOrCreateCell(uuid);
    }

    @Nullable
    private LegacyInfinityDataStorage getWritableCellStorage() {
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
            this.totalAEKeyType = cellStorage.amounts.size();
            this.totalAEKey2Amounts = cellStorage.itemCount == null ? BigInteger.ZERO : cellStorage.itemCount;
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
        tag.putUUID(InfinityConstants.INFINITY_CELL_UUID, newUUID);
        this.self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.cellUuid = newUUID;
        return newUUID;
    }

    private BigInteger getTotalAEKey2Amounts() {
        return this.totalAEKey2Amounts;
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

    private Object2ObjectMap<AEKey, BigInteger> getCellStoredMap() {
        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return Object2ObjectMaps.emptyMap();
        }
        return cellStorage.amounts;
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
        if (what instanceof AEItemKey itemKey
                && itemKey.getItem() instanceof InfinityBigIntegerCellItem
                && itemKey.get(DataComponents.CUSTOM_DATA) != null) {
            return 0;
        }

        var cellStorage = this.getWritableCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        BigInteger currentAmount = cellStorage.amounts.getOrDefault(what, BigInteger.ZERO);
        if (mode == Actionable.MODULATE) {
            BigInteger delta = BigInteger.valueOf(amount);
            if (currentAmount.equals(BigInteger.ZERO)) {
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
        if (this.storageManager == null) {
            return 0;
        }

        var cellStorage = this.getExistingCellStorage();
        if (cellStorage == null) {
            return 0;
        }

        BigInteger currentAmount = cellStorage.amounts.getOrDefault(what, BigInteger.ZERO);
        if (currentAmount.compareTo(BigInteger.ZERO) <= 0) {
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
            return currentAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    ? Long.MAX_VALUE : currentAmount.longValue();
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

    @Override
    public void getAvailableStacks(KeyCounter out) {
        BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
        for (var entry : this.getCellStoredMap().object2ObjectEntrySet()) {
            AEKey key = entry.getKey();
            BigInteger value = entry.getValue();

            long existing = out.get(key);
            BigInteger sum = BigInteger.valueOf(existing).add(value);
            long toSet = sum.compareTo(maxLong) > 0 ? Long.MAX_VALUE : sum.longValue();
            if (existing == Long.MAX_VALUE) {
                continue;
            }
            long delta = toSet - existing;
            if (delta != 0) {
                out.add(key, delta);
            }
        }
    }

    @Override
    public Component getDescription() {
        return this.self.getHoverName();
    }

    static final class LegacyInfinityDataStorage {
        private final Object2ObjectMap<AEKey, BigInteger> amounts = new Object2ObjectOpenHashMap<>();
        private BigInteger itemCount = BigInteger.ZERO;
    }

    static LegacyInfinityDataStorage createSeededStorage(AEKey[] keys, long amount) {
        LegacyInfinityDataStorage storage = new LegacyInfinityDataStorage();
        BigInteger value = BigInteger.valueOf(amount);
        for (AEKey key : keys) {
            storage.amounts.put(key, value);
        }
        storage.itemCount = value.multiply(BigInteger.valueOf(keys.length));
        return storage;
    }

    static final class LegacyInfinityStorageManager {
        private final Map<UUID, LegacyInfinityDataStorage> cells = new HashMap<>();

        private void setDirty() {
            // The old SavedData dirty flag has no observable effect in this benchmark.
        }

        void updateCell(UUID uuid, LegacyInfinityDataStorage storage) {
            cells.put(uuid, storage);
            setDirty();
        }

        private void removeCell(UUID uuid) {
            cells.remove(uuid);
            setDirty();
        }

        private boolean hasUUID(UUID uuid) {
            return cells.containsKey(uuid);
        }

        @Nullable
        private LegacyInfinityDataStorage getCell(UUID uuid) {
            return cells.get(uuid);
        }

        private LegacyInfinityDataStorage getOrCreateCell(UUID uuid) {
            if (!cells.containsKey(uuid)) {
                updateCell(uuid, new LegacyInfinityDataStorage());
            }
            return cells.get(uuid);
        }
    }
}
