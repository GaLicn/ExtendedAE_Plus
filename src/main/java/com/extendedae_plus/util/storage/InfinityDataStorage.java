package com.extendedae_plus.util.storage;

import appeng.api.stacks.AEKey;
import appeng.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import javax.annotation.Nullable;
import java.math.BigInteger;

public class InfinityDataStorage {
    public static final InfinityDataStorage EMPTY = new InfinityDataStorage();
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int LONG_MAX_BIT_LENGTH = Long.SIZE - 1;
    public final Object2LongMap<AEKey> longAmounts;
    public final Object2ObjectMap<AEKey, BigInteger> bigAmounts;
    private long longItemCount;
    @Nullable
    private BigInteger bigItemCount;
    @Nullable
    private BigInteger cachedItemCount;

    public InfinityDataStorage() {
        this(new Object2LongOpenHashMap<>(), new Object2ObjectOpenHashMap<>(), BigInteger.ZERO);
    }

    private InfinityDataStorage(Object2LongMap<AEKey> longAmounts,
            Object2ObjectMap<AEKey, BigInteger> bigAmounts, BigInteger itemCount) {
        this.longAmounts = longAmounts;
        this.bigAmounts = bigAmounts;
        setItemCount(itemCount);
    }

    public BigInteger getItemCount() {
        if (cachedItemCount != null) return cachedItemCount;
        BigInteger result;
        if (bigItemCount == null) result = BigInteger.valueOf(longItemCount);
        else if (longItemCount == 0) result = bigItemCount;
        else result = bigItemCount.add(BigInteger.valueOf(longItemCount));
        cachedItemCount = result;
        return result;
    }

    public boolean hasItems() {
        return !longAmounts.isEmpty() || !bigAmounts.isEmpty();
    }

    public int size() {
        return longAmounts.size() + bigAmounts.size();
    }

    public void insert(AEKey key, long amount) {
        if (amount <= 0) return;
        long current = longAmounts.getLong(key);
        if (current > 0) {
            if (amount <= Long.MAX_VALUE - current) longAmounts.put(key, current + amount);
            else {
                longAmounts.removeLong(key);
                bigAmounts.put(key, BigInteger.valueOf(current).add(BigInteger.valueOf(amount)));
            }
        } else {
            BigInteger big = bigAmounts.get(key);
            if (big == null) longAmounts.put(key, amount);
            else bigAmounts.put(key, big.add(BigInteger.valueOf(amount)));
        }
        addToItemCount(amount);
    }

    public long getExtractableAmount(AEKey key, long amount) {
        if (amount <= 0) return 0;
        long current = longAmounts.getLong(key);
        if (current > 0) return Math.min(current, amount);
        return bigAmounts.containsKey(key) ? amount : 0;
    }

    public long extract(AEKey key, long amount, boolean modulate) {
        if (!modulate) return getExtractableAmount(key, amount);
        if (amount <= 0) return 0;
        long current = longAmounts.getLong(key);
        if (current > 0) {
            long extracted = Math.min(current, amount);
            if (extracted == current) longAmounts.removeLong(key);
            else longAmounts.put(key, current - extracted);
            subtractFromItemCount(extracted);
            return extracted;
        }
        BigInteger big = bigAmounts.get(key);
        if (big == null || big.signum() <= 0) return 0;
        BigInteger remaining = big.subtract(amount == Long.MAX_VALUE ? BI_LONG_MAX : BigInteger.valueOf(amount));
        if (remaining.signum() <= 0) bigAmounts.remove(key);
        else if (remaining.bitLength() <= LONG_MAX_BIT_LENGTH) {
            bigAmounts.remove(key);
            longAmounts.put(key, remaining.longValue());
        } else bigAmounts.put(key, remaining);
        subtractFromItemCount(amount);
        return amount;
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        ListTag keys = new ListTag();
        ListTag amounts = new ListTag();
        for (var entry : Object2LongMaps.fastIterable(longAmounts)) {
            if (entry.getLongValue() <= 0) continue;
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            entry.getKey().toTagGeneric(output);
            keys.add(output.buildResult());
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("value", encodePositiveLong(entry.getLongValue()));
            amounts.add(tag);
        }
        for (var entry : Object2ObjectMaps.fastIterable(bigAmounts)) {
            if (entry.getValue() == null || entry.getValue().signum() <= 0) continue;
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            entry.getKey().toTagGeneric(output);
            keys.add(output.buildResult());
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("value", entry.getValue().toByteArray());
            amounts.add(tag);
        }
        nbt.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        nbt.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amounts);
        nbt.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, getItemCount().toByteArray());
        return nbt;
    }

    public static InfinityDataStorage loadFromNBT(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS).orElse(new ListTag());
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS).orElse(new ListTag());
        if (keys.size() != amounts.size()) AELog.warn("Loading storage cell with mismatched amounts/tags: %d != %d", amounts.size(), keys.size());
        Object2LongMap<AEKey> longValues = new Object2LongOpenHashMap<>(Math.max(2, keys.size()));
        Object2ObjectMap<AEKey, BigInteger> bigValues = new Object2ObjectOpenHashMap<>();
        int limit = Math.min(keys.size(), amounts.size());
        for (int i = 0; i < limit; i++) {
            AEKey key = AEKey.fromTagGeneric(TagValueInput.create(ProblemReporter.DISCARDING, registries, keys.getCompound(i).orElseThrow()));
            BigInteger amount = new BigInteger(amounts.getCompound(i).orElseThrow().getByteArray("value").orElse(new byte[0]));
            if (key == null || amount.signum() <= 0) continue;
            bigValues.remove(key);
            longValues.removeLong(key);
            if (amount.bitLength() <= LONG_MAX_BIT_LENGTH) longValues.put(key, amount.longValue());
            else bigValues.put(key, amount);
        }
        return new InfinityDataStorage(longValues, bigValues, calculateItemCount(longValues, bigValues));
    }

    private static BigInteger calculateItemCount(Object2LongMap<AEKey> longs, Object2ObjectMap<AEKey, BigInteger> bigs) {
        BigInteger result = BigInteger.ZERO;
        for (var entry : Object2LongMaps.fastIterable(longs)) result = result.add(BigInteger.valueOf(entry.getLongValue()));
        for (var entry : Object2ObjectMaps.fastIterable(bigs)) result = result.add(entry.getValue());
        return result;
    }

    private static byte[] encodePositiveLong(long value) {
        int count = (64 - Long.numberOfLeadingZeros(value) + 7) >>> 3;
        if (count == 0) count = 1;
        if ((value & (1L << (count * 8 - 1))) != 0) count++;
        byte[] result = new byte[count];
        for (int i = count - 1; i >= 0; i--) { result[i] = (byte) value; value >>>= 8; }
        return result;
    }

    private void setItemCount(BigInteger value) {
        cachedItemCount = null;
        if (value == null || value.signum() <= 0) { longItemCount = 0; bigItemCount = null; }
        else if (value.bitLength() <= LONG_MAX_BIT_LENGTH) { longItemCount = value.longValue(); bigItemCount = null; }
        else { longItemCount = 0; bigItemCount = value; }
    }

    private void addToItemCount(long amount) {
        cachedItemCount = null;
        if (bigItemCount != null) {
            if (longItemCount <= Long.MAX_VALUE - amount) longItemCount += amount;
            else { bigItemCount = getItemCount().add(BigInteger.valueOf(amount)); longItemCount = 0; }
        } else if (amount <= Long.MAX_VALUE - longItemCount) longItemCount += amount;
        else { bigItemCount = BigInteger.valueOf(longItemCount).add(BigInteger.valueOf(amount)); longItemCount = 0; }
    }

    private void subtractFromItemCount(long amount) {
        cachedItemCount = null;
        if (bigItemCount != null && longItemCount >= Long.MIN_VALUE + amount) longItemCount -= amount;
        else if (bigItemCount != null) setItemCount(bigItemCount.add(BigInteger.valueOf(longItemCount)).subtract(BigInteger.valueOf(amount)));
        else longItemCount -= amount;
    }
}
