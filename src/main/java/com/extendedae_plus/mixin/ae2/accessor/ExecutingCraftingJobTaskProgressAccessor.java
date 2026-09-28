package com.extendedae_plus.mixin.ae2.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface ExecutingCraftingJobTaskProgressAccessor {
    @Accessor("value")
    long eap$getValue();
}
