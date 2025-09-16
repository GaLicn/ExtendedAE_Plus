package com.extendedae_plus.util.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * InfinityDataStorage
 *
 * 表示单个 UUID 对应的持久化数据容器，直接映射到世界存档中的一项记录。
 * 数据结构说明：
 * - keys: 存放序列化后的 AEKey（每项为 CompoundTag），用于标识不同的存储条目
 * - amounts: 与 keys 一一对应的数量列表（每项为 CompoundTag），采用混合表示：
 *     - 当数量能放入 long 时，CompoundTag 包含键 "l" 存放 long 值
 *     - 当数量超出 long 时，CompoundTag 包含键 "s" 存放 BigInteger 的字符串形式
 *
 * 该类提供将内存数据与 NBT 之间互转的辅助方法，供 `SavedData` 在世界保存/加载时调用。
 */
public class InfinityDataStorage {

    // 不再暴露可变的共享实例，避免多个调用方修改同一 ListTag 导致交叉污染
    private static final InfinityDataStorage TRUE_EMPTY = new InfinityDataStorage(new ListTag(), new ListTag());

    /** 序列化的键列表（NBT ListTag，元素为 CompoundTag） */
    private ListTag keys;
    /**
     * 与 keys 对应的数量列表（NBT ListTag，元素为 CompoundTag）：
     * - 若数量能放入 long，则 CompoundTag 包含键 "l"(long)
     * - 否则包含键 "s"(String) 存放 BigInteger 的字符串形式
     */
    private ListTag amounts;

    public InfinityDataStorage() {
        this(new ListTag(), new ListTag());
    }

    private InfinityDataStorage(ListTag keys, ListTag amounts) {
        this.keys = keys;
        this.amounts = amounts;
    }

    /**
     * 返回一个空的不可共享实例（调用方若需要可变副本请自行复制）
     */
    public static InfinityDataStorage empty() {
        // 返回一个新的实例以避免共享可变对象被篡改
        return new InfinityDataStorage(new ListTag(), new ListTag());
    }

    /**
     * 将当前数据封装为 CompoundTag 以写入存档
     */
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("keys", keys);
        nbt.put("amounts", amounts);
        return nbt;
    }

    /**
     * 从存档读取数据并构造实例
     */
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt) {
        ListTag stackKeys = nbt.getList("keys", Tag.TAG_COMPOUND);
        // amounts 以 CompoundTag 列表存储，每个 CompoundTag 内含 long 或 String
        ListTag stackAmounts = nbt.getList("amounts", Tag.TAG_COMPOUND);
        return new InfinityDataStorage(stackKeys, stackAmounts);
    }

    public ListTag getKeys() {
        return keys;
    }

    public ListTag getAmounts() {
        return amounts;
    }

    public void setKeys(ListTag keys) {
        this.keys = keys;
    }

    public void setAmounts(ListTag amounts) {
        this.amounts = amounts;
    }
}
