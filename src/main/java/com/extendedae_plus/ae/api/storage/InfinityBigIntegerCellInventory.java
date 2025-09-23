package com.extendedae_plus.ae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.AELog;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityConstants;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * This code is inspired by AE2Things[](https://github.com/Technici4n/AE2Things-Forge), licensed under the MIT License.<p>
 * Original copyright (c) Technici4n<p>
 */
public class InfinityBigIntegerCellInventory implements StorageCell {
    private final InfinityBigIntegerCellItem cell;
    // 磁盘本身
    private final ItemStack self;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    // 存储物品键和数量的映射
    private Object2ObjectMap<AEKey, BigInteger> AEKey2AmountsMap;
    // 存储的物品种类数量
    private int totalAEKeyType;
    // 存储的物品总数
    private BigInteger totalAEKey2Amounts = BigInteger.ZERO;
    // 标记是否已持久化到 SavedData
    private boolean isPersisted = true;


    public InfinityBigIntegerCellInventory(InfinityBigIntegerCellItem cell, ItemStack stack, ISaveProvider saveProvider) {
        // 保存存储单元类型（InfinityBigIntegerCellItem 实例），用于访问磁盘属性
        this.cell = cell;
        // 保存物品堆栈，表示磁盘本身，包含运行时的 NBT 数据
        this.self = stack;
        // 保存提供者，用于触发数据保存
        this.container = saveProvider;
        // 初始化 storedAmounts 为 null，延迟加载物品数据
        this.AEKey2AmountsMap = null;
        // 初始化磁盘数据
        initData();
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

    // 获取磁盘的 InfinityDataStorage 数据
    private InfinityDataStorage getCellStorage() {
        // 如果磁盘有 UUID，返回对应的 InfinityDataStorage
        if (getUUID() != null) {
            return getStorageManagerInstance().getOrCreateCell(getUUID());
        } else {
            // 否则返回空的 InfinityDataStorage
            return InfinityDataStorage.EMPTY;
        }
    }

    // 初始化磁盘数据
    private void initData() {
        // 如果磁盘有 UUID，加载存储的物品数据
        if (hasUUID()) {
            this.totalAEKeyType = getCellStorage().amounts.size();
            this.totalAEKey2Amounts = getCellStorage().itemCount.equals(BigInteger.ZERO) ?
                    BigInteger.ZERO :
                    getCellStorage().itemCount;

        } else {
            // 否则初始化为空
            this.totalAEKeyType = 0;
            this.totalAEKey2Amounts = BigInteger.ZERO;
            // 加载物品数据
            getCellStoredMap();
        }
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

        if (totalAEKey2Amounts.equals(BigInteger.ZERO)) {
            if (hasUUID()) {
                getStorageManagerInstance().removeCell(getUUID());
                if (self.hasTag()) {
                    var tag = self.getTag();
                    // remove persisted identifiers and cached summary fields from the ItemStack
                    tag.remove(InfinityConstants.INFINITY_CELL_UUID);
                    tag.remove(InfinityConstants.INFINITY_ITEM_TOTAL);
                    tag.remove(InfinityConstants.INFINITY_ITEM_TYPES);
                    // backward compat: also remove internal cell item count key if present
                    tag.remove(InfinityConstants.INFINITY_CELL_ITEM_COUNT);
                }
                initData();
            }
            return;
        }

        // 创建物品键列表
        ListTag keys = new ListTag();
        // 创建物品数量列表
        ListTag amounts = new ListTag();
        // 初始化物品总数
        BigInteger itemCount = BigInteger.ZERO;

        for (var entry : this.AEKey2AmountsMap.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            // 如果数量大于 0，添加到键和数量列表
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                keys.add(entry.getKey().toTagGeneric());
                CompoundTag amountTag = new CompoundTag();
                amountTag.putByteArray("value", amount.toByteArray());
                amounts.add(amountTag);

                itemCount = itemCount.add(amount);
            }
        }

        if (keys.isEmpty()) {
            getStorageManagerInstance().updateCell(getUUID(), new InfinityDataStorage());
        } else {
            getStorageManagerInstance().modifyDisk(getUUID(), keys, amounts, itemCount);
        }

        // 更新存储的物品种类数量
        this.totalAEKeyType = this.AEKey2AmountsMap.size();
        // 更新存储的物品总数
        this.totalAEKey2Amounts = itemCount;
        // 将物品总数与种类数量存入物品堆栈的 NBT（用于快捷查看／tooltip），同时保留旧字段以兼容历史版本
        var tag = self.getOrCreateTag();
        tag.putByteArray(InfinityConstants.INFINITY_ITEM_TOTAL, itemCount.toByteArray());
        tag.putInt(InfinityConstants.INFINITY_ITEM_TYPES, this.totalAEKeyType);
        // backward compat storage field (kept for legacy readers)
        tag.putByteArray(InfinityConstants.INFINITY_CELL_ITEM_COUNT, itemCount.toByteArray());

        // 标记数据已持久化
        this.isPersisted = true;
    }

    // 获取存储单元的描述（此处返回null，可自定义）
    @Override
    public Component getDescription() {
        return null;
    }

    // 静态方法，创建存储单元库存
    public static InfinityBigIntegerCellInventory createInventory(ItemStack stack, ISaveProvider saveProvider) {
        // 检查物品堆栈是否为空
        Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
        // 检查物品是否为 IDISKCellItem 类型
        if (!(stack.getItem() instanceof InfinityBigIntegerCellItem cell)) {
            return null;
        }
        // 创建并返回新的 DISKCellInventory 实例
        return new InfinityBigIntegerCellInventory(cell, stack, saveProvider);
    }

    // 获取存储的物品总数
    public BigInteger getTotalAEKey2Amounts() {
        return this.totalAEKey2Amounts;
    }

    // 获取存储的物品种类数量
    public int getTotalAEKeyType() {
        return this.totalAEKeyType;
    }

    // 判断物品堆栈是否有UUID
    public boolean hasUUID() {
        return self.hasTag() && self.getOrCreateTag().contains(InfinityConstants.INFINITY_CELL_UUID);
    }

    // 获取物品堆栈的UUID
    public UUID getUUID() {
        if (this.hasUUID()) {
            return self.getOrCreateTag().getUUID(InfinityConstants.INFINITY_CELL_UUID);
        } else {
            return null;
        }
    }

    // 获取或初始化存储映射
    private Object2ObjectMap<AEKey, BigInteger> getCellStoredMap() {
        if (AEKey2AmountsMap == null) {
            AEKey2AmountsMap = new Object2ObjectOpenHashMap<>();
            this.loadCellStoredMap();
        }
        return AEKey2AmountsMap;
    }

    // 获取所有可用的物品堆栈及其数量
    @Override
    public void getAvailableStacks(KeyCounter out) {
        BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
        if(this.getCellStoredMap() == null) return;
        for (var entry : this.getCellStoredMap().object2ObjectEntrySet()) {
            AEKey key = entry.getKey();
            BigInteger value = entry.getValue();

            // 获取 KeyCounter 中已有的值
            long existing = out.get(key);

            // 计算总和并限制到 Long.MAX_VALUE
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


    // 从存储中加载物品映射
    private void loadCellStoredMap() {
        boolean dataCorruption = false;
        if (!self.hasTag()) return;

        var keys = getCellStorage().keys;
        var amounts = getCellStorage().amounts;
        // 数据损坏
        if (keys.size() != amounts.size()) {
            AELog.warn("Loading storage cell with mismatched amounts/tags: %d != %d", amounts.size(), keys.size());
        }
        // 遍历数量和键，加载到 AEKey2AmountsMap
        for (int i = 0; i < amounts.size(); i++) {
            AEKey key = AEKey.fromTagGeneric(keys.getCompound(i));
            BigInteger amount = new BigInteger(amounts.getCompound(i).getByteArray("value"));
            // 检查数据是否损坏
            if (amount.compareTo(BigInteger.ZERO) <= 0 || key == null) {
                dataCorruption = true;
            } else {
                AEKey2AmountsMap.put(key, amount);
            }
        }
        if (dataCorruption) {
            this.saveChanges();
        }
    }

    // 获取全局存储实例
    private static InfinityStorageManager getStorageManagerInstance() {
        return ExtendedAEPlus.STORAGE_INSTANCE;
    }

    // 标记数据需要保存，并通知容器或直接持久化
    private void saveChanges() {
        // 更新存储的物品种类数量
        this.totalAEKeyType = this.AEKey2AmountsMap.size();
        // 重置物品总数
        this.totalAEKey2Amounts = BigInteger.ZERO;
        // 计算物品总数
        for (BigInteger AEKey2Amounts : this.AEKey2AmountsMap.values()) {
            this.totalAEKey2Amounts = this.totalAEKey2Amounts.add(AEKey2Amounts);
        }
        // 标记数据未持久化
        this.isPersisted = false;
        // 如果有保存提供者，通知保存
        if (this.container != null) {
            this.container.saveChanges();
        } else {
            // 否则立即持久化
            this.persist();
        }
    }

    // 插入物品到存储单元
    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 数量为0或类型不匹配直接返回
        if (amount == 0){
            return 0;
        }
        // 不允许存储无限单元自身
        if (what instanceof AEItemKey itemKey && itemKey.getItem() instanceof InfinityBigIntegerCellItem) {
            return 0;
        }

        // 如果没有UUID，尝试在服务器端且存储管理器已就绪时生成UUID并初始化存储
        if (!this.hasUUID()) {
           self.getOrCreateTag().putUUID(InfinityConstants.INFINITY_CELL_UUID, UUID.randomUUID());
            getStorageManagerInstance().getOrCreateCell(getUUID());
            loadCellStoredMap();
        }
        // 获取当前物品数量
        BigInteger currentAmount = this.getCellStoredMap().getOrDefault(what, BigInteger.ZERO);

        if (mode == Actionable.MODULATE) {
            // 实际插入，更新数量并保存
            BigInteger newAmount = currentAmount.add(BigInteger.valueOf(amount));
            getCellStoredMap().put(what, newAmount);
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
                    getCellStoredMap().remove(what);
                    this.saveChanges();
                }
                return currentAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : currentAmount.longValue();
            } else {
                // 提取部分数量
                if (mode == Actionable.MODULATE) {
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
}
