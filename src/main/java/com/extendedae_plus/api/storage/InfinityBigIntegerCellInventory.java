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
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.UUID;

/** Storage-cell implementation for the Devourer disk. */
public class InfinityBigIntegerCellInventory implements StorageCell {
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger BI_THOUSAND = BigInteger.valueOf(1_000);
    private static final BigDecimal DECIMAL_THOUSAND = BigDecimal.valueOf(1_000);
    private static final String[] BIG_INTEGER_UNITS = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};

    private final InfinityBigIntegerCellItem cell;
    private final ItemStack self;
    @Nullable
    private UUID cellUuid;
    @Nullable
    private final InfinityStorageManager storageManager;
    @Nullable
    private InfinityDataStorage cachedCellStorage;
    private long cachedStorageRevision = Long.MIN_VALUE;
    private final ISaveProvider container;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final boolean hasPartitionFilter;
    private boolean isPersisted = true;

    /** Compatibility constructor used by the 1.20 cell handler. */
    public InfinityBigIntegerCellInventory(InfinityBigIntegerCellItem cell,
                                           ItemStack stack,
                                           ISaveProvider saveProvider) {
        this(cell, stack, saveProvider, ExtendedAEPlus.STORAGE_INSTANCE);
    }

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

    public static String formatBigInteger(BigInteger number) {
        if (number.compareTo(BI_THOUSAND) < 0) {
            return number.toString();
        }

        DecimalFormat df = new DecimalFormat("#.##");
        BigDecimal value = new BigDecimal(number);
        int unitIndex = 0;
        while (value.compareTo(DECIMAL_THOUSAND) >= 0
                && unitIndex < BIG_INTEGER_UNITS.length - 1) {
            value = value.divide(DECIMAL_THOUSAND, 2, RoundingMode.HALF_UP);
            unitIndex++;
        }
        return df.format(value.doubleValue()) + BIG_INTEGER_UNITS[unitIndex];
    }

    public static InfinityBigIntegerCellInventory createInventory(ItemStack stack,
                                                                   ISaveProvider saveProvider) {
        Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
        if (!(stack.getItem() instanceof InfinityBigIntegerCellItem cell)) {
            return null;
        }
        return new InfinityBigIntegerCellInventory(cell, stack, saveProvider);
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

        InfinityDataStorage storage = this.getExistingCellStorage();
        if (storage == null) {
            storage = this.storageManager.getOrCreateCell(uuid);
            this.cachedCellStorage = storage;
            this.cachedStorageRevision = this.storageManager.getStorageRevision();
        }
        return storage;
    }

    @Override
    public CellState getStatus() {
        InfinityDataStorage storage = this.getExistingCellStorage();
        return storage != null && storage.hasItems() ? CellState.NOT_EMPTY : CellState.EMPTY;
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

        InfinityDataStorage storage = this.getExistingCellStorage();
        BigInteger totalAmount = storage == null ? BigInteger.ZERO : storage.getItemCount();
        int itemTypes = storage == null ? 0 : storage.size();

        CompoundTag tag = this.self.getOrCreateTag();
        if (totalAmount.signum() <= 0) {
            tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
            tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
            tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
        } else {
            byte[] itemCountBytes = totalAmount.toByteArray();
            tag.putByteArray(InfinityConstants.INFINITY_ITEM_TOTAL, itemCountBytes);
            tag.putInt(InfinityConstants.INFINITY_ITEM_TYPES, itemTypes);
            tag.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, itemCountBytes);
        }

        this.isPersisted = true;
    }

    private void clearCellData() {
        UUID uuid = this.getUUID();
        if (uuid != null && this.storageManager != null) {
            this.storageManager.removeCell(uuid);
        }

        CompoundTag tag = this.self.getOrCreateTag();
        tag.remove(InfinityConstants.INFINITY_CELL_UUID);
        tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
        tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
        tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
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
        CompoundTag tag = this.self.getOrCreateTag();
        UUID newUUID = UUID.randomUUID();
        tag.putUUID(InfinityConstants.INFINITY_CELL_UUID, newUUID);
        this.cellUuid = newUUID;
        return newUUID;
    }

    @Nullable
    private UUID readUUIDFromStack() {
        CompoundTag tag = this.self.getTag();
        return tag != null && tag.contains(InfinityConstants.INFINITY_CELL_UUID)
                ? tag.getUUID(InfinityConstants.INFINITY_CELL_UUID)
                : null;
    }

    public UUID getUUID() {
        return this.cellUuid;
    }

    public boolean hasUUID() {
        return this.cellUuid != null;
    }

    /** Compatibility accessor retained for existing integrations. */
    public BigInteger getTotalAEKey2Amounts() {
        InfinityDataStorage storage = this.getExistingCellStorage();
        return storage == null ? BigInteger.ZERO : storage.getItemCount();
    }

    /** Compatibility accessor retained for existing integrations. */
    public int getTotalAEKeyType() {
        InfinityDataStorage storage = this.getExistingCellStorage();
        return storage == null ? 0 : storage.size();
    }

    /** Compatibility accessor retained for existing integrations. */
    public String getTotalStorage() {
        return formatBigInteger(this.getTotalAEKey2Amounts());
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
        if (amount <= 0 || this.storageManager == null) {
            return 0;
        }
        if (this.hasPartitionFilter && !this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }
        if (what instanceof AEItemKey itemKey
                && itemKey.getItem() instanceof InfinityBigIntegerCellItem
                && itemKey.hasTag()) {
            return 0;
        }

        // Simulations are frequent during AE2 planning. Do not allocate a UUID,
        // create SavedData entries, or touch the ItemStack on this path.
        if (mode != Actionable.MODULATE) {
            return amount;
        }

        InfinityDataStorage storage = this.getWritableCellStorage();
        if (storage == null) {
            return 0;
        }
        storage.insert(what, amount);
        this.saveChanges();
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || this.storageManager == null) {
            return 0;
        }

        InfinityDataStorage storage = this.getExistingCellStorage();
        if (storage == null) {
            return 0;
        }

        if (mode != Actionable.MODULATE) {
            return storage.getExtractableAmount(what, amount);
        }

        long extractedAmount = storage.extract(what, amount, true);
        if (extractedAmount > 0) {
            if (storage.size() == 0) {
                this.clearCellData();
            } else {
                this.saveChanges();
            }
        }
        return extractedAmount;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        InfinityDataStorage storage = this.getExistingCellStorage();
        if (storage == null || !storage.hasItems()) {
            return;
        }

        // AE2 normally starts aggregation with an empty counter. Avoid a lookup
        // for every unique key in that common case.
        boolean outputWasEmpty = out.isEmpty();
        for (var entry : Object2LongMaps.fastIterable(storage.longAmounts)) {
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

        for (var entry : Object2ObjectMaps.fastIterable(storage.bigAmounts)) {
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
            if (existing >= 0) {
                out.set(key, Long.MAX_VALUE);
            } else {
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
