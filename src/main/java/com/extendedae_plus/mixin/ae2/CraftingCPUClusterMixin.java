package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUClusterMixin {
    // Redirect the second call (ordinal=1) to getAcceleratorThreads in addBlockEntity,
    // which is used for the per-block <=16 validation in AE2. We return 1 so the check always passes.
    @Redirect(
        method = "addBlockEntity(Lappeng/blockentity/crafting/CraftingBlockEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/blockentity/crafting/CraftingBlockEntity;getAcceleratorThreads()I",
            ordinal = 1
        )
    )
    private int extendedae_plus$onGetThreadsForLimitCheck(CraftingBlockEntity te) {
        return 1;
    }
}
