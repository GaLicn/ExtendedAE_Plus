package com.extendedae_plus.util.storage;

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

    public ListTag keys;
    public ListTag amounts;
    // 存储磁盘中物品的总数，使用 BigInteger 支持大容量
    public BigInteger itemCount;

    public InfinityDataStorage() {
        this(new ListTag(), new ListTag(), BigInteger.ZERO);
    }

    private InfinityDataStorage(ListTag keys, ListTag amounts, BigInteger itemCount) {
        this.keys = keys;
        this.amounts = amounts;
        this.itemCount = itemCount;
    }

    // 将 DataStorage 数据序列化为 NBT 格式
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        nbt.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amounts);
        nbt.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, itemCount.toByteArray());
        return nbt;
    }

    // 从 NBT 数据反序列化创建 DataStorage 实例
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS, ListTag.TAG_COMPOUND);
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS, ListTag.TAG_COMPOUND);
        BigInteger itemCount = new BigInteger(nbt.getByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT));
        // 使用加载的数据创建新的 DataStorage 实例
        return new InfinityDataStorage(keys, amounts, itemCount);
    }
}
