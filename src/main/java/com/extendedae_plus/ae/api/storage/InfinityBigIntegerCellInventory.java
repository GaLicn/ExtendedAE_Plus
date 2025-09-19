package com.extendedae_plus.ae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
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
import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * InfinityBigIntegerCellInventory
 * <p>
 * 本类实现 AE2 的 StorageCell，表示单个 Infinity 存储单元的运行时数据与行为。
 * 主要职责：
 * - 在内存中维护条目映射 (AEKey -> BigInteger 数量)
 * - 提供插入/提取/列举/持久化等操作的实现
 * - 通过 UUID 将 ItemStack 与世界级的 SavedData 关联以实现持久化
 * <p>
 * 重要字段：
 * - stack: 关联的 ItemStack，NB T 中保存 UUID 与缓存信息
 * - container: AE2 提供的保存回调 (ISaveProvider)，用于合并与触发持久化
 * - storedMap: 延迟初始化的内存映射，减少未使用时内存占用
 * - totalStored: 缓存的总数量 (BigInteger)，避免频繁全表扫描
 * - isPersisted: 标记内存状态是否已同步到持久层
 */
public class InfinityBigIntegerCellInventory implements StorageCell {

    // 待持久化队列（用于 debounce：在服务器 tick 中合并持久化）
    private static final ConcurrentLinkedQueue<InfinityBigIntegerCellInventory> PENDING_PERSIST = new ConcurrentLinkedQueue<>();
    // 数字格式化对象，保留两位小数（复用以减少对象分配）
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    // 关联的 ItemStack（含可能的 uuid NBT）
    private final ItemStack stack;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    // 内存中的键-数量映射（使用 BigInteger 支持超长数量，延迟初始化）
    private Object2ObjectMap<AEKey, BigInteger> storedMap = null;
    // 标记是否已持久化到 SavedData
    private boolean isPersisted = true;


    /**
     * 私有构造器：通过 createInventory 工厂方法调用
     *
     * @param stack        关联的物品堆
     * @param saveProvider AE2 的保存回调（可为 null）
     */
    private InfinityBigIntegerCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        this.stack = stack;
        this.container = saveProvider;
        this.storedMap = null;
        initData();
    }

    // 将 BigInteger 格式化为带单位的字符串，保留两位小数
    public static String formatBigInteger(BigInteger number) {
        // 使用局部 DF（非线程安全），但 Minecraft 通常在主线程运行
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
        return DF.format(bd.doubleValue()) + units[idx];
    }

    // 创建存储单元库存实例的静态方法
    static InfinityBigIntegerCellInventory createInventory(ItemStack stack, ISaveProvider saveProvider) {
        return new InfinityBigIntegerCellInventory(stack, saveProvider);
    }

    private void initData() {
        if (!hasUUID()) {
            getCellStoredMap();
        }
    }

    // 获取当前存储单元的数据存储对象
    private InfinityDataStorage getCellStorage() {
        if (this.getUUID() == null) {
            // 如果没有UUID，返回空存储
            return InfinityDataStorage.EMPTY;
        } else {
            return InfinityStorageManager.INSTANCE.getOrCreateCell(this.getUUID());
        }
    }

    // 获取存储单元状态（空/非空）
    @Override
    public CellState getStatus() {
        if (this.getCellStoredMap().isEmpty()) {
            return CellState.EMPTY;
        }
        return CellState.NOT_EMPTY;
    }

    // 获取存储单元的待机能耗
    @Override
    public double getIdleDrain() {
        return 512;
    }

    // 获取存储单元的描述（此处返回null，可自定义）
    @Override
    public Component getDescription() {
        return null;
    }

    // 判断物品堆栈是否有UUID
    public boolean hasUUID() {
        return stack.hasTag() && stack.getOrCreateTag().contains("uuid");
    }

    // 获取物品堆栈的UUID
    public UUID getUUID() {
        if (this.hasUUID()) {
            return stack.getOrCreateTag().getUUID("uuid");
        } else {
            return null;
        }
    }

    // 获取或初始化存储映射
    private Object2ObjectMap<AEKey, BigInteger> getCellStoredMap() {
        if (storedMap == null) {
            storedMap = new Object2ObjectOpenHashMap<>();
            this.loadCellStoredMap();
        }
        return storedMap;
    }

    // 从存储中加载物品映射
    private void loadCellStoredMap() {
        boolean corruptedTag = false; // 标记数据是否损坏
        if (!stack.hasTag())
            return;
        ListTag keys = this.getCellStorage().keys;
        ListTag amounts = this.getCellStorage().amounts;


        for (int i = 0; i < amounts.size(); i++) {
            AEKey key = AEKey.fromTagGeneric(keys.getCompound(i));
            CompoundTag amtTag = amounts.getCompound(i);
            try {
                BigInteger amount;
                if (amtTag.contains("l")) {
                    long v = amtTag.getLong("l");
                    amount = BigInteger.valueOf(v);
                } else if (amtTag.contains("s")) {
                    amount = new BigInteger(amtTag.getString("s"));
                } else {
                    corruptedTag = true;
                    continue;
                }
                if (amount.compareTo(BigInteger.ZERO) <= 0 || key == null) {
                    corruptedTag = true;
                } else {
                    // storedMap 已在 getCellStoredMap() 中初始化，直接使用字段以避免额外方法开销
                    getCellStoredMap().put(key, amount);
                }
            } catch (NumberFormatException ex) {
                corruptedTag = true;
            }
        }
        // 如果有损坏，尝试保存修正后的数据；若全局管理器尚未就绪则保守处理
        if (corruptedTag) {
            this.saveChanges();
        }
    }

    // 标记数据需要保存，并通知容器或直接持久化
    private void saveChanges() {
        // 标记为未持久化，交由容器或延迟任务合并写入以减少 I/O
        isPersisted = false;
        if (container != null) {
            // 当存在容器时，优先让容器统一处理持久化
            container.saveChanges();
        } else {
            persist();
        }
    }

    // 获取所有可用的物品堆栈及其数量
    @Override
    public void getAvailableStacks(KeyCounter out) {
        BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
        Object2ObjectMap<AEKey, BigInteger> map = getCellStoredMap();
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : map.object2ObjectEntrySet()) {
            AEKey key = entry.getKey();
            BigInteger value = entry.getValue();

            // 当前 KeyCounter 中已有的值（long）
            long existing = out.get(key);

            // 将 existing 与当前 value 做 BigInteger 累加并饱和到 Long.MAX_VALUE
            BigInteger sum = BigInteger.valueOf(existing).add(value);
            long toSet = sum.compareTo(maxLong) > 0 ? Long.MAX_VALUE : sum.longValue();

            // KeyCounter 没有 set(key,long) 的统一接口暴露（只有 add/remove），所以先移除已存在的值再设置。
            // 为避免读取-写入竞争，我们计算出要新增的 delta 并调用 add(key, delta)
            if (existing == Long.MAX_VALUE) {
                // 已经饱和，无需再添加
                continue;
            }
            long delta;
            if (toSet == Long.MAX_VALUE) {
                delta = Long.MAX_VALUE - existing;
            } else {
                delta = toSet - existing;
            }
            if (delta != 0) {
                out.add(key, delta);
            }
        }
    }

    // 持久化存储单元数据到全局存储
    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }
        Object2ObjectMap<AEKey, BigInteger> map = this.getCellStoredMap();
        if (map.isEmpty()) {
            // 如果存储为空，保守处理：写回空的 persisted 数据但不要从 ItemStack 上移除 uuid
            if (this.hasUUID()) {
                // 如果存储为空，移除UUID和全局存储中的数据，并清理缓存的 types/total
                if (hasUUID()) {
                    InfinityStorageManager.INSTANCE.removeCell(getUUID());
                    if (stack.getTag() != null) {
                        stack.getTag().remove("uuid");
                        stack.getTag().remove("types");
                        stack.getTag().remove("total");
                    }
                    initData();
                }
                return;
            }
        }
        // 构建要保存的Key和数量列表（混合表示：long 或 string）
        ListTag amountTags = new ListTag();
        ListTag keys = new ListTag();
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : map.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                keys.add(entry.getKey().toTagGeneric());
                CompoundTag amt = new CompoundTag();
                if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                    amt.putLong("l", amount.longValue());
                } else {
                    amt.putString("s", amount.toString());
                }
                amountTags.add(amt);
            }
        }
        if (keys.isEmpty()) {
            InfinityStorageManager.INSTANCE.updateCell(this.getUUID(), new InfinityDataStorage());
            // 清理缓存
            if (stack.getTag() != null) {
                stack.getTag().remove("types");
                stack.getTag().remove("total");
            }
        } else {
            // amounts 现在为 CompoundTag 列表
            InfinityStorageManager.INSTANCE.modifyCell(this.getUUID(), keys, amountTags);
            // 缓存类型数量与总量到 ItemStack 的 NBT，避免每次 tooltip 或展示时重新统计
            try {
                if (stack.getTag() == null) stack.setTag(new CompoundTag());
                int typesCount = keys.size();
                stack.getOrCreateTag().putInt("types", typesCount);
                java.math.BigInteger total = java.math.BigInteger.ZERO;
                for (java.util.Map.Entry<AEKey, java.math.BigInteger> e : map.object2ObjectEntrySet()) {
                    java.math.BigInteger v = e.getValue();
                    if (v.compareTo(java.math.BigInteger.ZERO) > 0) {
                        total = total.add(v);
                    }
                }
                if (total.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                    stack.getOrCreateTag().putLong("total", total.longValue());
                } else {
                    stack.getOrCreateTag().putString("total", total.toString());
                }
            } catch (Exception ignored) {
            }
        }
        isPersisted = true;
    }

    // 插入物品到存储单元
    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 数量为0或类型不匹配直接返回
        if (amount == 0) {
            return 0;
        }
        // 不允许存储无限单元自身
        if (what instanceof AEItemKey itemKey && itemKey.getItem() instanceof InfinityBigIntegerCellItem) {
            return 0;
        }
        // 如果没有UUID，生成UUID并初始化存储（延迟创建全局存储以避免在 manager 未就绪时 NPE）
        if (!this.hasUUID()) {
            stack.getOrCreateTag().putUUID("uuid", UUID.randomUUID());
            InfinityStorageManager.INSTANCE.getOrCreateCell(getUUID());
            // 确保 storedMap 初始化并从持久层加载数据
            loadCellStoredMap();
        }
        Object2ObjectMap<AEKey, BigInteger> map = this.getCellStoredMap();
        BigInteger currentAmount = map.getOrDefault(what, BigInteger.ZERO);
        if (mode == Actionable.MODULATE) {
            // 实际插入，更新数量并保存
            BigInteger newAmount = currentAmount.add(BigInteger.valueOf(amount));
            map.put(what, newAmount);
            this.saveChanges();
        }
        return amount;
    }

    // 从存储单元提取物品
    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        Object2ObjectMap<AEKey, BigInteger> map = this.getCellStoredMap();
        BigInteger currentAmount = map.getOrDefault(what, BigInteger.ZERO);
        if (currentAmount.compareTo(BigInteger.ZERO) > 0) {
            BigInteger requested = BigInteger.valueOf(amount);
            if (currentAmount.compareTo(requested) <= 0) {
                // 提取全部
                long ret;
                if (currentAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                    ret = Long.MAX_VALUE;
                } else {
                    ret = currentAmount.longValue();
                }
                if (mode == appeng.api.config.Actionable.MODULATE) {
                    map.remove(what);
                    this.saveChanges();
                }
                return ret;
            } else {
                // 提取部分
                if (mode == Actionable.MODULATE) {
                    map.put(what, currentAmount.subtract(requested));
                    this.saveChanges();
                }
                return amount;
            }
        }
        return 0;
    }

    // 获取存储单元内所有物品的总数量（格式化字符串）
    public String getTotalStorage() {
        // 使用缓存的 totalStored，避免每次全表扫描
        BigInteger total = BigInteger.ZERO;
        for (BigInteger value : getCellStoredMap().values()) {
            if (value.compareTo(BigInteger.ZERO) > 0) {
                total = total.add(value);
            }
        }
        return formatBigInteger(total);
    }
}
