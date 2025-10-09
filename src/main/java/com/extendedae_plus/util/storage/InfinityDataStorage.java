package com.extendedae_plus.util.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * MIT License
 *
 * Copyright (c) 2025 Frostbite-time
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */

/**
 * 无尽元件的数据实际读写
 * @author Frostbite
 */
public class InfinityDataStorage {
    // EMPTY也不需要，因为ae的硬盘获取实际上是允许返回null的，详见InfinityBigIntegerCellHandler的修改

    /** 同一个磁盘，在内存中总是只维护一份map，也就是这个。其他人永远只使用其引用 */
    private final Object2ObjectMap<AEKey, BigInteger> AEKey2Amounts;

    public InfinityDataStorage() {
        this(new Object2ObjectOpenHashMap<>());
    }

    public InfinityDataStorage(Object2ObjectMap<AEKey, BigInteger> maps) {
        AEKey2Amounts = maps;
    }

    public Object2ObjectMap<AEKey, BigInteger> getAEKey2Amounts()
    {
        return AEKey2Amounts;
    }

    // 将 DataStorage 数据序列化为 NBT 格式
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider registry) {
        // 创建物品键列表
        ListTag keys = new ListTag();
        // 创建物品数量列表
        ListTag amounts = new ListTag();

        for (var entry : this.AEKey2Amounts.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            // 如果数量大于 0，添加到键和数量列表
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                try {
                    keys.add(entry.getKey().toTagGeneric(registry));
                    CompoundTag amountTag = new CompoundTag();
                    amountTag.putByteArray("value", amount.toByteArray());
                    amounts.add(amountTag);
                }
                catch (Exception e) {
                    // 静默所有错误
                }
            }
        }

        CompoundTag KVTags = new CompoundTag();
        KVTags.put(InfinityConstants.INFINITY_CELL_KEYS, keys);
        KVTags.put(InfinityConstants.INFINITY_CELL_AMOUNTS, amounts);
        return KVTags;
    }

    // 从 NBT 数据反序列化创建 DataStorage 实例
    public static InfinityDataStorage loadFromNBT(CompoundTag nbt, @NotNull HolderLookup.Provider registry) {
        ListTag keys = nbt.getList(InfinityConstants.INFINITY_CELL_KEYS, ListTag.TAG_COMPOUND);
        ListTag amounts = nbt.getList(InfinityConstants.INFINITY_CELL_AMOUNTS, ListTag.TAG_COMPOUND);

        Object2ObjectMap<AEKey, BigInteger> maps = new Object2ObjectOpenHashMap<>();
        maps.defaultReturnValue(BigInteger.ZERO);
        for (int i = 0; i < keys.size(); i++) {
            try {
                AEKey key = AEKey.fromTagGeneric(registry,keys.getCompound(i));
                BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
                if (amount.compareTo(BigInteger.ZERO) <= 0 || key == null) continue;
                maps.put(AEKey.fromTagGeneric(registry, keys.getCompound(i)), new BigInteger(amounts.getCompound(i).getByteArray("value")));
            }
            catch (Exception e) {
                // 忽略错误，保证读取时不崩溃
                // 这里我静默处理所有错误，后续你可以自己做其他处理
            }
        }

        return new InfinityDataStorage(maps);
    }
}
