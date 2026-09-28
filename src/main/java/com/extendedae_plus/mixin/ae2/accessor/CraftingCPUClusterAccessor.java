package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CraftingCPUClusterAccessor {
    @Invoker("updateOutput")
    void eap$invokeUpdateOutput(GenericStack finalOutput);
}
