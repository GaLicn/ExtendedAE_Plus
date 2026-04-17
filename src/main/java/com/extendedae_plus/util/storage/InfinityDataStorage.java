package com.extendedae_plus.util.storage;

import appeng.api.stacks.AEKey;
import appeng.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.math.BigInteger;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityDataStorage {
    // 定义一个静态常量 EMPTY，表示一个空的 DataStorage 实例，用于默认或占位场景
    public static final InfinityDataStorage EMPTY = new InfinityDataStorage();

    // 运行时权威数据结构，避免在高频路径上反复构造和拆解 NBT
    public final Object2ObjectMap<AEKey, BigInteger> amounts;
    // 存储磁盘中物品的总数，使用 BigInteger 支持大容量
    public BigInteger itemCount;

    public InfinityDataStorage() {
        this(new Object2ObjectOpenHashMap<>(), BigInteger.ZERO);
    }

    private InfinityDataStorage(Object2ObjectMap<AEKey, BigInteger> amounts, BigInteger itemCount) {
        this.amounts = amounts;
        this.itemCount = itemCount;
    }

    // 将 DataStorage 数据序列化为 NBT 格式，保持旧版字段结构兼容
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag keys = new ListTag();
        ListTag amountsTag = new ListTag();

        for (var entry : this.amounts.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
                continue;
            }

            keys.add(entry.getKey().toTagGeneric());
            CompoundTag amountTag = new CompoundTag();
            amountTag.putByteArray("value", amount.toByteArray());
            amountsTag.add(amountTag);
        }

        nbt.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        nbt.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amountsTag);
        nbt.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, this.itemCount.toByteArray());
        return nbt;
    }

    // 从 NBT 数据反序列化创建 DataStorage 实例，兼容旧版列表式存档结构
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS, ListTag.TAG_COMPOUND);
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS, ListTag.TAG_COMPOUND);
        if (keys.size() != amounts.size()) {
            AELog.warn("Loading storage cell with mismatched amounts/tags: %d != %d", amounts.size(), keys.size());
        }

        Object2ObjectMap<AEKey, BigInteger> storedAmounts = new Object2ObjectOpenHashMap<>();
        BigInteger computedItemCount = BigInteger.ZERO;
        int limit = Math.min(keys.size(), amounts.size());
        for (int i = 0; i < limit; i++) {
            AEKey key = AEKey.fromTagGeneric(keys.getCompound(i));
            BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
            if (key == null || amount.compareTo(BigInteger.ZERO) <= 0) {
                continue;
            }

            storedAmounts.put(key, amount);
            computedItemCount = computedItemCount.add(amount);
        }

        return new InfinityDataStorage(storedAmounts, computedItemCount);
    }
}
