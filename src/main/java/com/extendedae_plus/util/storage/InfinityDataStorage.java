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
    // 大数基值和 long 增量合并后的读取缓存，下一次数量变更时失效
    @Nullable
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

    // 获取精确总数。只有存在尚未合并的增量时才进行一次 BigInteger 加法
    public BigInteger getItemCount() {
        BigInteger cachedItemCount = this.cachedItemCount;
        if (cachedItemCount != null) {
            return cachedItemCount;
        }

        BigInteger itemCount;
        if (this.bigItemCount == null) {
            itemCount = BigInteger.valueOf(this.longItemCount);
        } else if (this.longItemCount == 0) {
            itemCount = this.bigItemCount;
        } else {
            itemCount = this.bigItemCount.add(BigInteger.valueOf(this.longItemCount));
            // 总数重新落入 long 范围时立即降级，避免后续增量逼近 long 边界。
            if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                this.setItemCount(itemCount);
            }
        }

        this.cachedItemCount = itemCount;
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

        long currentAmount = this.longAmounts.getLong(key);
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

    // 模拟提取只读取一个数量 Map，不进行 BigInteger 运算或库存状态更新。
    public long getExtractableAmount(AEKey key, long amount) {
        if (amount <= 0) {
            return 0;
        }

        long currentAmount = this.longAmounts.getLong(key);
        if (currentAmount > 0) {
            return Math.min(currentAmount, amount);
        }

        // bigAmounts 只保存超过 Long.MAX_VALUE 的数量，可满足任意正 long 请求。
        BigInteger currentBigAmount = this.bigAmounts.get(key);
        return currentBigAmount != null && currentBigAmount.signum() > 0 ? amount : 0;
    }

    // 提取物品。模拟操作走独立只读路径，实际操作才修改 Map 和总数
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

        // 正常情况下 BigInteger 表中的数量一定超过 Long.MAX_VALUE。
        // 只有该数量被提取回 long 范围时，才将它移回 long Map。
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
            amountTag.putByteArray("value", encodePositiveLong(amount));
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
        for (int i = 0; i < limit; i++) {
            AEKey key = AEKey.fromTagGeneric(registries, keys.getCompound(i));
            BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
            if (key == null || amount.signum() <= 0) {
                continue;
            }

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

    /**
     * 根据两个数量 Map 重新计算磁盘内的物品总数。
     *
     * <p>普通数量优先使用 long 累加；只有总数即将超过 {@link Long#MAX_VALUE}
     * 时才创建并使用 BigInteger，避免加载大量普通物品时为每一项都创建大整数对象。</p>
     *
     * <p>调用方传入的 Map 已经是最终状态，因此这里计算的是每个 key 当前数量之和，
     * 不使用存档中可能过期的总数摘要。</p>
     */
    private static BigInteger calculateItemCount(Object2LongMap<AEKey> longAmounts,
                                                  Object2ObjectMap<AEKey, BigInteger> bigAmounts) {
        // 在总数没有溢出前，先用原生 long 保存累计值。
        long longItemCount = 0;
        // null 表示当前总数仍然可以完全用 long 表示。
        BigInteger bigItemCount = null;

        // 先处理数量位于 long Map 中的普通物品。
        for (var entry : Object2LongMaps.fastIterable(longAmounts)) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }

            // 通过减法判断是否会溢出，避免直接相加后再检查结果。
            if (bigItemCount == null && amount <= Long.MAX_VALUE - longItemCount) {
                longItemCount += amount;
            } else {
                // 第一次溢出时，把此前累计的 long 数量转为 BigInteger 基值。
                if (bigItemCount == null) {
                    bigItemCount = BigInteger.valueOf(longItemCount);
                }
                bigItemCount = bigItemCount.add(BigInteger.valueOf(amount));
                // 已合并到 BigInteger，long 只作为下一段临时累计值使用。
                longItemCount = 0;
            }
        }

        // BigInteger Map 中的数量本身已经超过 long 范围，必须使用大整数相加。
        for (var entry : Object2ObjectMaps.fastIterable(bigAmounts)) {
            BigInteger amount = entry.getValue();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }

            // 如果此前只有普通数量，则先把普通数量并入 BigInteger 基值。
            if (bigItemCount == null) {
                bigItemCount = BigInteger.valueOf(longItemCount);
                longItemCount = 0;
            }
            bigItemCount = bigItemCount.add(amount);
        }

        // 没有发生大数溢出时直接返回 long 结果，否则返回精确的 BigInteger 结果。
        return bigItemCount == null ? BigInteger.valueOf(longItemCount) : bigItemCount;
    }

    /**
     * 将正 long 编码为与 BigInteger.toByteArray() 相同的最短有符号大端字节数组。
     *
     * <p>存档加载时会使用 BigInteger(byte[]) 解码，因此当最高有效位为 1 时必须
     * 增加一个值为 0 的符号字节，避免正数被误解码为负数。这样可以避免先创建
     * BigInteger 再调用 toByteArray() 的临时对象开销，同时保持旧存档格式兼容。</p>
     */
    private static byte[] encodePositiveLong(long value) {
        // 根据最高有效位计算保存该正数所需的最少字节数。
        int byteCount = (64 - Long.numberOfLeadingZeros(value) + 7) >>> 3;
        if (byteCount == 0) {
            // 当前调用方不会传入 0，但保留该分支可使方法独立处理 0。
            byteCount = 1;
        }

        // BigInteger 使用最高位作为符号位；最高位为 1 时需要补一个 0 字节。
        if ((value & (1L << (byteCount * 8 - 1))) != 0) {
            byteCount++;
        }

        byte[] encoded = new byte[byteCount];
        // 按 BigInteger.toByteArray() 的大端顺序写入字节。
        for (int index = byteCount - 1; index >= 0; index--) {
            encoded[index] = (byte) value;
            value >>>= 8;
        }
        return encoded;
    }

    /**
     * 将精确总数拆分保存为 long 或 BigInteger。
     *
     * <p>总数不超过 long 范围时走低成本的 long 路径；超过范围后只保存一个
     * BigInteger 基值，后续的小幅增量放在 longItemCount 中，减少高频读写时的
     * BigInteger 运算。</p>
     */
    private void setItemCount(BigInteger itemCount) {
        // 总数表示发生变化，之前缓存的精确结果不能继续使用。
        this.cachedItemCount = null;
        if (itemCount == null || itemCount.signum() <= 0) {
            // 空库存统一归零，不保留负数或 null 状态。
            this.longItemCount = 0;
            this.bigItemCount = null;
        } else if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
            // BigInteger 仍在正 long 范围内，降级回 long 存储。
            this.longItemCount = itemCount.longValue();
            this.bigItemCount = null;
        } else {
            // 超出 long 范围时保存完整精确值作为大数基值。
            this.longItemCount = 0;
            this.bigItemCount = itemCount;
        }
    }

    /**
     * 增加磁盘总数，并尽量保持在 long 快速路径中。
     *
     * @param amount 本次输入的正数量
     */
    private void addToItemCount(long amount) {
        // 输入会改变总数，先使惰性缓存失效。
        this.cachedItemCount = null;
        if (this.bigItemCount != null) {
            // 大数基值已经存在时，优先把增量暂存在 long 中。
            if (this.longItemCount <= Long.MAX_VALUE - amount) {
                this.longItemCount += amount;
            } else {
                // 增量自身即将溢出时，才与 BigInteger 基值合并并清空临时增量。
                this.bigItemCount = this.bigItemCount
                        .add(BigInteger.valueOf(this.longItemCount))
                        .add(BigInteger.valueOf(amount));
                this.longItemCount = 0;
            }
        } else if (amount <= Long.MAX_VALUE - this.longItemCount) {
            // 总数仍未溢出，直接使用 long 累加。
            this.longItemCount += amount;
        } else {
            // 本次输入导致总数首次超过 long 范围，转换为 BigInteger。
            this.bigItemCount = BigInteger.valueOf(this.longItemCount).add(BigInteger.valueOf(amount));
            this.longItemCount = 0;
        }
    }

    /**
     * 减少磁盘总数，并在总数回落到 long 范围时自动降级。
     *
     * @param amount 本次输出的正数量
     */
    private void subtractFromItemCount(long amount) {
        // 输出会改变总数，先使惰性缓存失效。
        this.cachedItemCount = null;
        if (this.bigItemCount != null) {
            // 允许 long 增量暂时为负数，表示从大数基值中扣除的数量。
            if (this.longItemCount >= Long.MIN_VALUE + amount) {
                this.longItemCount -= amount;
            } else {
                // 负增量即将溢出时，与大数基值合并并重新选择 long/BigInteger 表示。
                this.setItemCount(this.bigItemCount
                        .add(BigInteger.valueOf(this.longItemCount))
                        .subtract(BigInteger.valueOf(amount)));
            }
        } else {
            // 未使用 BigInteger 时，直接从 long 总数中扣除。
            this.longItemCount -= amount;
        }
    }
}
