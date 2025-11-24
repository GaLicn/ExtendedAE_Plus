package com.extendedae_plus.mixin.advancedae.accessor;

import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public interface AdvCraftingCPULogicAccessor {

    @Accessor("job")
    ExecutingCraftingJob eap$getAdvJob();
}
