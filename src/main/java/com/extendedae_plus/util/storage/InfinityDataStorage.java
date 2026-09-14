package com.extendedae_plus.util.storage;

import appeng.api.stacks.AEKey;
import appeng.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.math.BigInteger;

/** Runtime and persistent data for one infinity storage cell. */
public class InfinityDataStorage {
    public static final InfinityDataStorage EMPTY = new InfinityDataStorage();

    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int LONG_MAX_BIT_LENGTH = Long.SIZE - 1;

    /** Quantities that fit in a positive long. */
    public final Object2LongMap<AEKey> longAmounts;
    /** Quantities that exceed the positive long range. */
    public final Object2ObjectMap<AEKey, BigInteger> bigAmounts;

    // The total is split in the same way as the per-key quantities. This avoids a
    // BigInteger allocation for every ordinary insert/extract operation.
    private long longItemCount;
    private BigInteger bigItemCount;
    private BigInteger cachedItemCount;

    public InfinityDataStorage() {
        this(new Object2LongOpenHashMap<>(), new Object2ObjectOpenHashMap<>(), BigInteger.ZERO);
    }

    private InfinityDataStorage(Object2LongMap<AEKey> longAmounts,
                                Object2ObjectMap<AEKey, BigInteger> bigAmounts,
                                BigInteger itemCount) {
        this.longAmounts = longAmounts;
        this.bigAmounts = bigAmounts;
        this.setItemCount(itemCount);
    }

    /** Builds storage from an already populated long map without per-entry BigIntegers. */
    public static InfinityDataStorage fromLongAmounts(Object2LongMap<AEKey> longAmounts) {
        if (longAmounts == null) {
            throw new IllegalArgumentException("longAmounts must not be null");
        }
        Object2ObjectMap<AEKey, BigInteger> emptyBigAmounts = new Object2ObjectOpenHashMap<>();
        return new InfinityDataStorage(longAmounts, emptyBigAmounts,
                calculateItemCount(longAmounts, emptyBigAmounts));
    }

    /** Returns the exact total, calculating the mixed representation lazily. */
    public BigInteger getItemCount() {
        BigInteger cached = this.cachedItemCount;
        if (cached != null) {
            return cached;
        }

        BigInteger itemCount;
        if (this.bigItemCount == null) {
            itemCount = BigInteger.valueOf(this.longItemCount);
        } else if (this.longItemCount == 0) {
            itemCount = this.bigItemCount;
        } else {
            itemCount = this.bigItemCount.add(BigInteger.valueOf(this.longItemCount));
            if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                this.setItemCount(itemCount);
            }
        }

        this.cachedItemCount = itemCount;
        return itemCount;
    }

    public boolean hasItems() {
        return !this.longAmounts.isEmpty() || !this.bigAmounts.isEmpty();
    }

    public int size() {
        return this.longAmounts.size() + this.bigAmounts.size();
    }

    /** Inserts a positive amount using the long fast path whenever possible. */
    public void insert(AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }

        long currentAmount = this.longAmounts.getLong(key);
        if (currentAmount > 0) {
            if (amount <= Long.MAX_VALUE - currentAmount) {
                this.longAmounts.put(key, currentAmount + amount);
            } else {
                this.longAmounts.removeLong(key);
                this.bigAmounts.put(key,
                        BigInteger.valueOf(currentAmount).add(BigInteger.valueOf(amount)));
            }
        } else {
            BigInteger currentBigAmount = this.bigAmounts.get(key);
            if (currentBigAmount == null) {
                this.longAmounts.put(key, amount);
            } else {
                this.bigAmounts.put(key, currentBigAmount.add(BigInteger.valueOf(amount)));
            }
        }

        this.addToItemCount(amount);
    }

    /** Returns the amount available for a simulated extraction without mutation. */
    public long getExtractableAmount(AEKey key, long amount) {
        if (amount <= 0) {
            return 0;
        }

        long currentAmount = this.longAmounts.getLong(key);
        if (currentAmount > 0) {
            return Math.min(currentAmount, amount);
        }

        // Every entry in bigAmounts is above Long.MAX_VALUE by invariant.
        BigInteger currentBigAmount = this.bigAmounts.get(key);
        return currentBigAmount != null && currentBigAmount.signum() > 0 ? amount : 0;
    }

    /** Extracts an amount; only the modulating path mutates maps and totals. */
    public long extract(AEKey key, long amount, boolean modulate) {
        if (!modulate) {
            return this.getExtractableAmount(key, amount);
        }
        if (amount <= 0) {
            return 0;
        }

        long currentAmount = this.longAmounts.getLong(key);
        if (currentAmount > 0) {
            long extractedAmount = Math.min(currentAmount, amount);
            if (extractedAmount == currentAmount) {
                this.longAmounts.removeLong(key);
            } else {
                this.longAmounts.put(key, currentAmount - extractedAmount);
            }
            this.subtractFromItemCount(extractedAmount);
            return extractedAmount;
        }

        BigInteger currentBigAmount = this.bigAmounts.get(key);
        if (currentBigAmount == null || currentBigAmount.signum() <= 0) {
            return 0;
        }

        BigInteger removedAmount = amount == Long.MAX_VALUE
                ? BI_LONG_MAX
                : BigInteger.valueOf(amount);
        BigInteger remainingAmount = currentBigAmount.subtract(removedAmount);
        if (remainingAmount.signum() <= 0) {
            this.bigAmounts.remove(key);
        } else if (remainingAmount.bitLength() <= LONG_MAX_BIT_LENGTH) {
            this.bigAmounts.remove(key);
            this.longAmounts.put(key, remainingAmount.longValue());
        } else {
            this.bigAmounts.put(key, remainingAmount);
        }
        this.subtractFromItemCount(amount);
        return amount;
    }

    /** Serializes using the legacy list layout shared by all 1.20 saves. */
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag keys = new ListTag();
        ListTag amountsTag = new ListTag();

        for (var entry : Object2LongMaps.fastIterable(this.longAmounts)) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }

            keys.add(entry.getKey().toTagGeneric());
            CompoundTag amountTag = new CompoundTag();
            amountTag.putByteArray("value", encodePositiveLong(amount));
            amountsTag.add(amountTag);
        }

        for (var entry : Object2ObjectMaps.fastIterable(this.bigAmounts)) {
            BigInteger amount = entry.getValue();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }

            keys.add(entry.getKey().toTagGeneric());
            CompoundTag amountTag = new CompoundTag();
            amountTag.putByteArray("value", amount.toByteArray());
            amountsTag.add(amountTag);
        }

        nbt.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        nbt.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amountsTag);
        nbt.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, this.getItemCount().toByteArray());
        return nbt;
    }

    /** Loads both old BigInteger-only entries and the optimized representation. */
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS, ListTag.TAG_COMPOUND);
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS, ListTag.TAG_COMPOUND);
        if (keys.size() != amounts.size()) {
            AELog.warn("Loading storage cell with mismatched amounts/tags: %d != %d", amounts.size(), keys.size());
        }

        int limit = Math.min(keys.size(), amounts.size());
        Object2LongMap<AEKey> storedLongAmounts = new Object2LongOpenHashMap<>(Math.max(2, limit));
        Object2ObjectMap<AEKey, BigInteger> storedBigAmounts = new Object2ObjectOpenHashMap<>();
        for (int i = 0; i < limit; i++) {
            AEKey key = AEKey.fromTagGeneric(keys.getCompound(i));
            BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
            if (key == null || amount.signum() <= 0) {
                continue;
            }

            // A malformed/legacy save may contain the same key more than once.
            // Keep the last entry, matching the old Object2ObjectMap behavior.
            storedBigAmounts.remove(key);
            storedLongAmounts.removeLong(key);

            if (amount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                storedLongAmounts.put(key, amount.longValue());
            } else {
                storedBigAmounts.put(key, amount);
            }
        }

        return new InfinityDataStorage(storedLongAmounts, storedBigAmounts,
                calculateItemCount(storedLongAmounts, storedBigAmounts));
    }

    private static BigInteger calculateItemCount(Object2LongMap<AEKey> longAmounts,
                                                  Object2ObjectMap<AEKey, BigInteger> bigAmounts) {
        long longItemCount = 0;
        BigInteger bigItemCount = null;

        for (var entry : Object2LongMaps.fastIterable(longAmounts)) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }

            if (bigItemCount == null && amount <= Long.MAX_VALUE - longItemCount) {
                longItemCount += amount;
            } else {
                if (bigItemCount == null) {
                    bigItemCount = BigInteger.valueOf(longItemCount);
                }
                bigItemCount = bigItemCount.add(BigInteger.valueOf(amount));
                longItemCount = 0;
            }
        }

        for (var entry : Object2ObjectMaps.fastIterable(bigAmounts)) {
            BigInteger amount = entry.getValue();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }

            if (bigItemCount == null) {
                bigItemCount = BigInteger.valueOf(longItemCount);
                longItemCount = 0;
            }
            bigItemCount = bigItemCount.add(amount);
        }

        return bigItemCount == null ? BigInteger.valueOf(longItemCount) : bigItemCount;
    }

    /** Encodes a positive long exactly like BigInteger.toByteArray(). */
    private static byte[] encodePositiveLong(long value) {
        int byteCount = (64 - Long.numberOfLeadingZeros(value) + 7) >>> 3;
        if (byteCount == 0) {
            byteCount = 1;
        }
        if ((value & (1L << (byteCount * 8 - 1))) != 0) {
            byteCount++;
        }

        byte[] encoded = new byte[byteCount];
        for (int index = byteCount - 1; index >= 0; index--) {
            encoded[index] = (byte) value;
            value >>>= 8;
        }
        return encoded;
    }

    private void setItemCount(BigInteger itemCount) {
        this.cachedItemCount = null;
        if (itemCount == null || itemCount.signum() <= 0) {
            this.longItemCount = 0;
            this.bigItemCount = null;
        } else if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
            this.longItemCount = itemCount.longValue();
            this.bigItemCount = null;
        } else {
            this.longItemCount = 0;
            this.bigItemCount = itemCount;
        }
    }

    private void addToItemCount(long amount) {
        this.cachedItemCount = null;
        if (this.bigItemCount != null) {
            if (this.longItemCount <= Long.MAX_VALUE - amount) {
                this.longItemCount += amount;
            } else {
                this.bigItemCount = this.bigItemCount
                        .add(BigInteger.valueOf(this.longItemCount))
                        .add(BigInteger.valueOf(amount));
                this.longItemCount = 0;
            }
        } else if (amount <= Long.MAX_VALUE - this.longItemCount) {
            this.longItemCount += amount;
        } else {
            this.bigItemCount = BigInteger.valueOf(this.longItemCount).add(BigInteger.valueOf(amount));
            this.longItemCount = 0;
        }
    }

    private void subtractFromItemCount(long amount) {
        this.cachedItemCount = null;
        if (this.bigItemCount != null) {
            if (this.longItemCount >= Long.MIN_VALUE + amount) {
                this.longItemCount -= amount;
            } else {
                this.setItemCount(this.bigItemCount
                        .add(BigInteger.valueOf(this.longItemCount))
                        .subtract(BigInteger.valueOf(amount)));
            }
        } else {
            this.longItemCount -= amount;
        }
    }
}
