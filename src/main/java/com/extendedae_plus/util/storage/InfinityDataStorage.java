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

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * 本代码参考了 AE2Things[](https://github.com/Technici4n/AE2Things-Forge)，并遵循 MIT 许可证。<p>
 * 原始版权归 Technici4n 所有。<p>
 */
public class InfinityDataStorage {
    // 用于默认或占位场景
    public static final InfinityDataStorage EMPTY = new InfinityDataStorage();

    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int LONG_MAX_BIT_LENGTH = Long.SIZE - 1;

    // 普通数量使用原生 long 保存，避免高频存取时反复创建 BigInteger
    public final Object2LongMap<AEKey> longAmounts;
    // 单项数量超过 long 范围后，才转移到 BigInteger 存储
    public final Object2ObjectMap<AEKey, BigInteger> bigAmounts;
    // 总数未溢出时保存精确值；溢出后保存尚未合并到 BigInteger 基值的增量
    private long longItemCount;
    // 总数溢出后的基值。与 longItemCount 相加才是当前精确总数
    @Nullable
    private BigInteger bigItemCount;

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

    // 获取精确总数。只有存在尚未合并的增量时才进行一次 BigInteger 加法
    public BigInteger getItemCount() {
        if (this.bigItemCount == null) {
            return BigInteger.valueOf(this.longItemCount);
        }
        if (this.longItemCount == 0) {
            return this.bigItemCount;
        }

        BigInteger itemCount = this.bigItemCount.add(BigInteger.valueOf(this.longItemCount));
        // 总数重新落入 long 范围时立即降级，避免后续增量逼近 long 边界。
        if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
            this.setItemCount(itemCount);
        }
        return itemCount;
    }

    // 所有 Map 条目都保持为正数，直接检查 Map 可避免计算极大总数
    public boolean hasItems() {
        return !this.longAmounts.isEmpty() || !this.bigAmounts.isEmpty();
    }

    public int size() {
        return this.longAmounts.size() + this.bigAmounts.size();
    }

    // 增加物品数量。普通数量和总数未溢出时完全使用 long 快速路径
    public void insert(AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }

        long currentAmount = this.longAmounts.getOrDefault(key, 0L);
        if (currentAmount > 0) {
            if (amount <= Long.MAX_VALUE - currentAmount) {
                this.longAmounts.put(key, currentAmount + amount);
            } else {
                this.longAmounts.removeLong(key);
                this.bigAmounts.put(key, BigInteger.valueOf(currentAmount).add(BigInteger.valueOf(amount)));
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

    // 提取物品。模拟操作只读取数量，实际操作才修改 Map 和总数
    public long extract(AEKey key, long amount, boolean modulate) {
        if (amount <= 0) {
            return 0;
        }

        long currentAmount = this.longAmounts.getOrDefault(key, 0L);
        if (currentAmount > 0) {
            long extractedAmount = Math.min(currentAmount, amount);
            if (modulate) {
                if (extractedAmount == currentAmount) {
                    this.longAmounts.removeLong(key);
                } else {
                    this.longAmounts.put(key, currentAmount - extractedAmount);
                }
                this.subtractFromItemCount(extractedAmount);
            }
            return extractedAmount;
        }

        BigInteger currentBigAmount = this.bigAmounts.get(key);
        if (currentBigAmount == null || currentBigAmount.signum() <= 0) {
            return 0;
        }

        // 正常情况下 BigInteger 表中的数量一定超过 Long.MAX_VALUE。
        // 只有该数量被提取回 long 范围时，才将它移回 long Map。
        if (modulate) {
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
        }
        return amount;
    }

    // 将 DataStorage 数据序列化为 NBT 格式，保持旧版字段结构兼容
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        ListTag keys = new ListTag();
        ListTag amountsTag = new ListTag();

        for (var entry : Object2LongMaps.fastIterable(this.longAmounts)) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }

            keys.add(entry.getKey().toTagGeneric(registries));
            CompoundTag amountTag = new CompoundTag();
            amountTag.putByteArray("value", BigInteger.valueOf(amount).toByteArray());
            amountsTag.add(amountTag);
        }

        for (var entry : Object2ObjectMaps.fastIterable(this.bigAmounts)) {
            BigInteger amount = entry.getValue();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }

            keys.add(entry.getKey().toTagGeneric(registries));
            CompoundTag amountTag = new CompoundTag();
            amountTag.putByteArray("value", amount.toByteArray());
            amountsTag.add(amountTag);
        }

        nbt.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        nbt.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amountsTag);
        nbt.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, this.getItemCount().toByteArray());
        return nbt;
    }

    // 从 NBT 数据反序列化创建 DataStorage 实例，兼容旧版列表式存档结构
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS, ListTag.TAG_COMPOUND);
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS, ListTag.TAG_COMPOUND);
        if (keys.size() != amounts.size()) {
            AELog.warn("Loading storage cell with mismatched amounts/tags: %d != %d", amounts.size(), keys.size());
        }

        int limit = Math.min(keys.size(), amounts.size());
        Object2LongMap<AEKey> storedLongAmounts = new Object2LongOpenHashMap<>(Math.max(2, limit));
        // 实际存档以 long 数量为主，极大数 Map 按需扩容，避免重复预留同等容量。
        Object2ObjectMap<AEKey, BigInteger> storedBigAmounts = new Object2ObjectOpenHashMap<>();
        BigInteger computedItemCount = BigInteger.ZERO;
        for (int i = 0; i < limit; i++) {
            AEKey key = AEKey.fromTagGeneric(registries, keys.getCompound(i));
            BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
            if (key == null || amount.signum() <= 0) {
                continue;
            }

            BigInteger previousAmount = storedBigAmounts.remove(key);
            if (storedLongAmounts.containsKey(key)) {
                previousAmount = BigInteger.valueOf(storedLongAmounts.removeLong(key));
            }

            if (amount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                storedLongAmounts.put(key, amount.longValue());
            } else {
                storedBigAmounts.put(key, amount);
            }

            computedItemCount = previousAmount == null
                    ? computedItemCount.add(amount)
                    : computedItemCount.subtract(previousAmount).add(amount);
        }

        return new InfinityDataStorage(storedLongAmounts, storedBigAmounts, computedItemCount);
    }

    private void setItemCount(BigInteger itemCount) {
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
        if (this.bigItemCount != null) {
            if (this.longItemCount <= Long.MAX_VALUE - amount) {
                this.longItemCount += amount;
            } else {
                // 增量即将溢出时才合并到 BigInteger 基值。
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
        if (this.bigItemCount != null) {
            if (this.longItemCount >= Long.MIN_VALUE + amount) {
                this.longItemCount -= amount;
            } else {
                // 负增量即将溢出时合并，并顺便尝试降级回 long 总数。
                this.setItemCount(this.bigItemCount
                        .add(BigInteger.valueOf(this.longItemCount))
                        .subtract(BigInteger.valueOf(amount)));
            }
        } else {
            this.longItemCount -= amount;
        }
    }
}
