package com.extendedae_plus.mixin.neoecoae.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob$TaskProgress", remap = false)
public interface ECOTaskProgressAccessor {
    @Accessor("value")
    long eap$getECOValue();
}
