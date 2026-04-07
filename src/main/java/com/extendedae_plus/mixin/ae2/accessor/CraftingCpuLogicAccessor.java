package com.extendedae_plus.mixin.ae2.accessor;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public interface CraftingCpuLogicAccessor {
    @Accessor("job")
    ExecutingCraftingJob eap$getJob();

    @Invoker("finishJob")
    void eap$invokeFinishJob(boolean success);
}
