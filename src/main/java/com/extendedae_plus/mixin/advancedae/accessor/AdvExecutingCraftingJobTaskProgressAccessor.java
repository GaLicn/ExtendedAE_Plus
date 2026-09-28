package com.extendedae_plus.mixin.advancedae.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface AdvExecutingCraftingJobTaskProgressAccessor {

    @Accessor("value")
    long eap$getAdvValue();
}
