package com.extendedae_plus.ae.api.storage;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
import net.minecraft.world.item.ItemStack;

/**
 * InfinityBigIntegerCellHandler
 *
 * 该类实现 AE2 的 ICellHandler，用于：
 * - 判定某个 ItemStack 是否为本 mod 的 Infinity 存储单元
 * - 在 AE2 请求访问或创建存储单元时，创建并返回对应的 StorageCell 实例
 */
public class InfinityBigIntegerCellHandler implements ICellHandler {

    /** Handler 单例，供注册与调用使用 */
    public static final InfinityBigIntegerCellHandler INSTANCE = new InfinityBigIntegerCellHandler();

    /**
     * 判断给定的 ItemStack 是否为 InfinityBigIntegerCell
     */
    @Override
    public boolean isCell(ItemStack is) {
        return is.getItem() instanceof InfinityBigIntegerCellItem;
    }

    /**
     * 在 AE2 需要访问或创建存储单元时返回对应的 InfinityBigIntegerCellInventory（StorageCell 实现）。
     * 参数 container 为 AE2 提供的保存回调（ISaveProvider），当 cell 需要持久化时会调用它。
     */
    @Override
    public InfinityBigIntegerCellInventory getCellInventory(ItemStack is, ISaveProvider container) {
        return InfinityBigIntegerCellInventory.createInventory(is, container);
    }
}