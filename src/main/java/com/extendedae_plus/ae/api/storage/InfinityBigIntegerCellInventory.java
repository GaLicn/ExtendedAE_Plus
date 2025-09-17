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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

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

    static {
        // 在类加载时注册服务器 tick 监听器，用于在主线程合并写入
        try {
            MinecraftForge.EVENT_BUS.addListener(InfinityBigIntegerCellInventory::onServerTick);
        } catch (Throwable ignored) {
            // 保守降级：若注册失败，不阻塞实例化
        }
    }

    // 关联的 ItemStack（含可能的 uuid NBT）
    private final ItemStack stack;
    // AE2 提供的保存提供者，用于在容器中批量保存时触发回调
    private final ISaveProvider container;
    // 内存中的键-数量映射（使用 BigInteger 支持超长数量，延迟初始化）
    private Object2ObjectMap<AEKey, BigInteger> storedMap = null;
    // 标记是否已持久化到 SavedData
    private boolean isPersisted = true;
    // 缓存的总存储量，避免每次调用进行全表扫描
    private BigInteger totalStored = BigInteger.ZERO;

    /**
     * 私有构造器：通过 createInventory 工厂方法调用
     *
     * @param stack        关联的物品堆
     * @param saveProvider AE2 的保存回调（可为 null）
     */
    private InfinityBigIntegerCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        this.stack = stack;
        container = saveProvider;
        // 不在构造时创建 storedMap，推迟到实际访问或首次写入时初始化
        this.storedMap = null;
    }

    // 创建存储单元库存实例的静态方法
    static InfinityBigIntegerCellInventory createInventory(ItemStack stack, ISaveProvider saveProvider) {
        if (stack.getItem() instanceof InfinityBigIntegerCellItem) {
            return new InfinityBigIntegerCellInventory(stack, saveProvider);
        }
        return null;
    }

    // 获取全局存储实例
    private static InfinityStorageManager getStorageInstance() {
        return InfinityStorageManager.INSTANCE;
    }

    // 服务器 tick 回调：合并并执行待持久化项
    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        InfinityBigIntegerCellInventory inv;
        // 处理本次 tick 中的全部待持久化项
        while ((inv = PENDING_PERSIST.poll()) != null) {
            try {
                if (!inv.isPersisted) {
                    inv.persist();
                }
            } catch (Throwable ignored) {
                // 忽略单项错误，继续处理其余队列
            }
        }
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

    // 获取当前存储单元的数据存储对象
    private InfinityDataStorage getCellStorage() {
        if (this.getUUID() == null) {
            // 如果没有UUID，返回空存储
            return InfinityDataStorage.EMPTY;
        } else {
            // 否则获取或创建对应UUID的存储
            return getStorageInstance().getOrCreateCell(getUUID());
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
        if (this.hasUUID())
            return stack.getOrCreateTag().getUUID("uuid");
        else
            return null;
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
        if (!stack.hasTag()) return;
        ListTag keys = this.getCellStorage().keys;
        ListTag amounts = this.getCellStorage().amounts;
        int len = Math.min(keys.size(), amounts.size());
        for (int i = 0; i < len; i++) {
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
                    storedMap.put(key, amount);
                    // 更新缓存的总数
                    totalStored = totalStored.add(amount);
                }
            } catch (NumberFormatException ex) {
                corruptedTag = true;
            }
        }
        // 如果有损坏，保存修正后的数据
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
            // 如果没有容器，入队等待服务器 tick 在主线程统一持久化，避免频繁 I/O
            if (!PENDING_PERSIST.contains(this)) {
                PENDING_PERSIST.offer(this);
            }
        }
    }

    // 获取所有可用的物品堆栈及其数量
    @Override
    public void getAvailableStacks(KeyCounter out) {
        // 使用饱和（saturating）加法将 BigInteger 值转换为 long 并安全地累加到 KeyCounter 中。
        // 问题背景：当同一物品存在于多个 cell 中，AE2 的 KeyCounter 使用 long 来记录数量，
        // 若简单将单个 cell 的超长值截断为 Long.MAX_VALUE 并直接 add，多个 cell 的合并会导致
        // 原本代表 "大于 long" 的值被重复添加而导致读取异常。解决方案：每次向 KeyCounter 添加前，
        // 先读取当前计数器中的已有值（long），并使用 BigInteger 做饱和加法后再写回为 long，避免中间溢出。

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
        if (this.isPersisted)
            return;
        Object2ObjectMap<AEKey, BigInteger> map = this.getCellStoredMap();
        if (map.isEmpty()) {
            // 如果存储为空，移除UUID和全局存储中的数据
            if (this.hasUUID()) {
                getStorageInstance().removeCell(getUUID());
                if (stack.getTag() != null) {
                    stack.getTag().remove("uuid");
                    // 移除缓存的 total 字段
                    stack.getTag().remove("total");
                }
            }
            return;
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
        // 如果没有Key，更新为空存储，否则保存数据
        if (keys.isEmpty()) {
            getStorageInstance().updateCell(this.getUUID(), new InfinityDataStorage());
        } else {
            // amounts 现在为 CompoundTag 列表
            getStorageInstance().modifyCell(this.getUUID(), keys, amountTags);
        }
        // 将缓存的 totalStored 同步到 ItemStack 的 NBT，优先使用 long
        if (stack.getOrCreateTag() != null) {
            if (totalStored.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                stack.getOrCreateTag().putLong("total", totalStored.longValue());
            } else {
                stack.getOrCreateTag().putString("total", totalStored.toString());
            }
            // 将当前已存储的不同物品种类数缓存到 NBT（键名: "types"），用于客户端 tooltip 显示
            int typesCount = this.getCellStoredMap().size();
            stack.getOrCreateTag().putInt("types", typesCount);
        }
        isPersisted = true;
    }

    // 插入物品到存储单元
    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 数量为0或类型不匹配直接返回
        if (amount == 0)
            return 0;
        // 不允许存储无限单元自身
        if (what instanceof AEItemKey itemKey && itemKey.getItem() instanceof InfinityBigIntegerCellItem)
            return 0;
        // 如果没有UUID，生成UUID并初始化存储
        if (!this.hasUUID()) {
            stack.getOrCreateTag().putUUID("uuid", UUID.randomUUID());
            getStorageInstance().getOrCreateCell(getUUID());
            // 确保 storedMap 初始化并从持久层加载数据
            this.getCellStoredMap();
        }
        Object2ObjectMap<AEKey, BigInteger> map = this.getCellStoredMap();
        BigInteger currentAmount = map.getOrDefault(what, BigInteger.ZERO);
        if (mode == Actionable.MODULATE) {
            // 实际插入，更新数量并保存
            BigInteger newAmount = currentAmount.add(BigInteger.valueOf(amount));
            map.put(what, newAmount);
            // 更新 cached total
            totalStored = totalStored.add(BigInteger.valueOf(amount));
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
                if (mode == Actionable.MODULATE) {
                    map.remove(what);
                    // 更新 cached total
                    // 如果 currentAmount 大于 Long.MAX_VALUE，totalStored 减去 currentAmount 会保留大整数
                    totalStored = totalStored.subtract(currentAmount);
                    this.saveChanges();
                }
                return ret;
            } else {
                // 提取部分
                if (mode == Actionable.MODULATE) {
                    map.put(what, currentAmount.subtract(requested));
                    // 更新 cached total
                    totalStored = totalStored.subtract(requested);
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
        return formatBigInteger(totalStored);
    }
}
