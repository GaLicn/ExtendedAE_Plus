package com.extendedae_plus.mixin.extendedae.accessor;

import appeng.api.inventories.InternalInventory;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TileAssemblerMatrixCrafter.class)
public interface TileAssemblerMatrixCrafterAccessor {

    @Accessor("threads")
    CraftingThread[] extendedae_plus$getThreads();

    @Accessor("threads")
    @Mutable
    void extendedae_plus$setThreads(CraftingThread[] threads);

    @Accessor("internalInv")
    @Mutable
    void extendedae_plus$setInternalInv(InternalInventory inventory);
}
