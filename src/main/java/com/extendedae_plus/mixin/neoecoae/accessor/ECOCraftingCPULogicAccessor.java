package com.extendedae_plus.mixin.neoecoae.accessor;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ECOCraftingCPULogic.class, remap = false)
public interface ECOCraftingCPULogicAccessor {
    @Accessor("job")
    ExecutingCraftingJob eap$getECOJob();

    @Invoker("finishJob")
    void eap$invokeECOFinishJob(boolean success);
}
