package com.extendedae_plus.ae.api.storage;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public class InfinityBigIntegerCellHandler implements ICellHandler {

    public static final InfinityBigIntegerCellHandler INSTANCE = new InfinityBigIntegerCellHandler();

    @Override
    public boolean isCell(ItemStack is) {
        return is.getItem() instanceof InfinityBigIntegerCellItem;
    }

    /** AE的这个方法是Nullable的，这里直接把NULL拦在外面即可 */
    @Override
    public @Nullable InfinityBigIntegerCellInventory getCellInventory(ItemStack is, @Nullable ISaveProvider container) {
        if(ServerLifecycleHooks.getCurrentServer() == null) return null;
        InfinityStorageManager manager = InfinityStorageManager.getInstance();
        if(manager == null || is == null) return null;
        return new InfinityBigIntegerCellInventory(is, container, manager);
    }
}