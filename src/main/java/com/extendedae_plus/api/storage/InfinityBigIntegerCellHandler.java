package com.extendedae_plus.api.storage;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import net.minecraft.world.item.ItemStack;

public class InfinityBigIntegerCellHandler implements ICellHandler {

    public static final InfinityBigIntegerCellHandler INSTANCE = new InfinityBigIntegerCellHandler();

    @Override
    public boolean isCell(ItemStack is) {
        return is.getItem() instanceof InfinityBigIntegerCellItem;
    }

    @Override
    public InfinityBigIntegerCellInventory getCellInventory(ItemStack is, ISaveProvider container) {
        return InfinityBigIntegerCellInventory.createInventory(is, container);
    }
}