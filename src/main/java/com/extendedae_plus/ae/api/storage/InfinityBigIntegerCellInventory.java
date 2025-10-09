package com.extendedae_plus.ae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.UUID;

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
 * 用于无限元件的存储，这个类永远只会在服务端被构造
 * @author Frostbite
 */
public class InfinityBigIntegerCellInventory implements StorageCell {
    // 磁盘本身
    @NotNull
    private final ItemStack self;
    @NotNull
    private final InfinityStorageManager storageManager;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    @Nullable
    private final ISaveProvider container;
    // 存储物品键和数量的映射
    @NotNull
    private final Object2ObjectMap<AEKey, BigInteger> AEKey2AmountsMap;
    // 存储的物品总数
    @NotNull
    private BigInteger totalAEKey2Amounts = BigInteger.ZERO;
    // 标记表示是否通知了需要保存
    private boolean isPersisted = false;


    public InfinityBigIntegerCellInventory(@NotNull ItemStack stack,
                                           @Nullable ISaveProvider saveProvider,
                                           @NotNull InfinityStorageManager storageManager) {
        // 保存物品堆栈，表示磁盘本身，包含运行时的 NBT 数据
        this.self = stack;
        // 保存提供者，用于触发数据保存
        this.container = saveProvider;
        this.storageManager = storageManager;
        // 走到这一步之后，我们不再管stack到底是什么物品，只要它能突破InfinityBigIntegerCellHandler的限制塞进来，那我们都接收
        // 这里立刻构建map，确保后续操作简洁，这里如果存在则取引用，如果不存在则建立空map，均不会造成性能问题
        this.AEKey2AmountsMap = storageManager.getOrCreateCell(getOrCreateUUID(stack, storageManager)).getAEKey2Amounts();

        // 第一次构建我们全量遍历数量，后续增量更新
        for(BigInteger amount : AEKey2AmountsMap.values())
        {
            totalAEKey2Amounts = totalAEKey2Amounts.add(amount);
        }
    }

    // 将 BigInteger 格式化为带单位的字符串，保留两位小数
    public static String formatBigInteger(BigInteger number) {
        // 使用方法局部的 DecimalFormat，避免静态共享的非线程安全问题
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

    // 获取存储单元的状态（空、部分填充）
    @Override
    public CellState getStatus() {
        // 如果没有存储任何物品，返回空状态
        if (this.getTotalAEKey2Amounts().equals(BigInteger.ZERO)) {
            return CellState.EMPTY;
        }
        // 否则返回满状态
        return CellState.NOT_EMPTY;
    }

    // 获取存储单元的待机能耗
    @Override
    public double getIdleDrain() {
        return 512;
    }

    // 持久化存储单元数据到全局存储
    @Override
    public void persist() {
        if (this.isPersisted)
            return;

        // 更新tooltip

        if (this.totalAEKey2Amounts.equals(BigInteger.ZERO)) {
            // 移除磁盘这一步有极少量优化效果，但是一个空map在内存中只占用极少的空间，持久化到磁盘中也只有极低的占用
            // 且SavedData并不限制存储的nbt的长度大小，因此，我们不再处理磁盘移除，简化数据管理逻辑
            CustomData data = self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
            tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
            // backward compat
            tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);

            self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return;
        }

        CompoundTag tag = self.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putByteArray(InfinityConstants.INFINITY_ITEM_TOTAL, this.totalAEKey2Amounts.toByteArray());
        tag.putInt(InfinityConstants.INFINITY_ITEM_TYPES, this.AEKey2AmountsMap.size());
        tag.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, this.totalAEKey2Amounts.toByteArray());
        self.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.isPersisted = true;
    }

    // 获取存储单元的描述
    @Override
    public Component getDescription() {
        return Component.empty();
    }

    // 获取存储的物品总数
    public @NotNull BigInteger getTotalAEKey2Amounts() {
        return this.totalAEKey2Amounts;
    }

    // 获取存储的物品种类数量
    public int getTotalAEKeyType() {
        return this.AEKey2AmountsMap.size();
    }

    // 获取或初始化存储映射
    private Object2ObjectMap<AEKey, BigInteger> getCellStoredMap() {
        return AEKey2AmountsMap;
    }

    // 获取所有可用的物品堆栈及其数量
    @Override
    public void getAvailableStacks(KeyCounter out) {
        BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
        for (var entry : this.getCellStoredMap().object2ObjectEntrySet()) {
            AEKey key = entry.getKey();
            BigInteger value = entry.getValue();
            if(value.equals(BigInteger.ZERO)) continue;

            // 计算总和并限制到 Long.MAX_VALUE
            long existing = out.get(key);
            BigInteger sum = BigInteger.valueOf(existing).add(value);
            long toSet = sum.compareTo(maxLong) > 0 ? Long.MAX_VALUE : sum.longValue();
            // 更新 KeyCounter
            if (existing == Long.MAX_VALUE) {
                continue;
            }
            long delta = toSet - existing;
            if (delta != 0) {
                out.add(key, delta);
            }
        }
    }

    // 标记数据需要保存，并通知容器或直接持久化
    private void saveChanges() {
        // 这里只需要这么多，container的统一持久化最终也会走persist
        this.isPersisted = false;
        if (this.container != null) {
            this.container.saveChanges();
        } else {
            this.persist();
        }
        storageManager.setDirty(); // 数据设脏，这里只是通知，不会导致立刻保存，后续磁盘会随着保存事件一起保存
    }

    // 插入物品到存储单元
    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 数量为0或类型不匹配直接返回
        if (amount == 0) {
            return 0;
        }

        //        这里存储自身不会造成实际上的问题，因为无限磁盘本身存储的nbt极为有限
        //        如果后续需要判断那些不允许被存储的磁盘，自行调用StroageCells中获取磁盘仓库的方法来判空 + canFitInsideCell
        //        这里不做过多处理
        //        if (what instanceof AEItemKey itemKey &&
        //                itemKey.getItem() instanceof InfinityBigIntegerCellItem &&
        //                itemKey.get(DataComponents.CUSTOM_DATA) != null
        //        ) {
        //            return 0;
        //        }

        // 获取当前物品数量
        BigInteger currentAmount = this.getCellStoredMap().getOrDefault(what, BigInteger.ZERO);

        if (mode == Actionable.MODULATE) {
            // 实际插入，更新数量并保存
            BigInteger toAdd = BigInteger.valueOf(amount);
            this.totalAEKey2Amounts = this.totalAEKey2Amounts.add(toAdd);
            getCellStoredMap().put(what, currentAmount.add(toAdd));
            this.saveChanges();
        }
        return amount;
    }

    // 从存储单元提取物品
    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        BigInteger currentAmount = this.getCellStoredMap().getOrDefault(what, BigInteger.ZERO);
        // 如果有物品可提取
        if (currentAmount.compareTo(BigInteger.ZERO) > 0) {

            BigInteger requested = BigInteger.valueOf(amount);

            // 如果提取数量大于等于当前数量
            if (requested.compareTo(currentAmount) >= 0) {
                if (mode == Actionable.MODULATE) {
                    this.totalAEKey2Amounts = this.totalAEKey2Amounts.subtract(currentAmount);
                    getCellStoredMap().remove(what);
                    this.saveChanges();
                }
                return currentAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : currentAmount.longValue();
            } else {
                // 提取部分数量
                if (mode == Actionable.MODULATE) {
                    this.totalAEKey2Amounts = this.totalAEKey2Amounts.subtract(requested);
                    getCellStoredMap().put(what, currentAmount.subtract(requested));
                    this.saveChanges();
                }
                return requested.longValue();
            }
        }
        return 0;
    }

    // 获取存储单元内所有物品的总数量（格式化字符串）
    public String getTotalStorage() {
        // 使用缓存的 totalStored，避免每次全表扫描
        return formatBigInteger(totalAEKey2Amounts);
    }

    // 能用来构建这个inv的物品必须要有一个UUID
    public static UUID getOrCreateUUID(ItemStack stack, InfinityStorageManager storageManager)
    {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if(!data.isEmpty() && data.copyTag().contains(InfinityConstants.INFINITY_CELL_UUID)) {
            return data.copyTag().getUUID(InfinityConstants.INFINITY_CELL_UUID);
        } else {
            UUID uuid;
            do {
                uuid = UUID.randomUUID();
            } while (storageManager.hasUUID(uuid)); // 防御一下
            CompoundTag tag = data.copyTag();
            tag.putUUID(InfinityConstants.INFINITY_CELL_UUID, uuid);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return uuid;
        }
    }
}
