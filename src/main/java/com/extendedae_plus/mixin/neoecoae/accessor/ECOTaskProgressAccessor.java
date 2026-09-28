package com.extendedae_plus.mixin.neoecoae.accessor;

// NeoECOAE 适配暂时禁用，待发布适配版本后恢复；build.gradle 已排除本文件。

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob$TaskProgress", remap = false)
public interface ECOTaskProgressAccessor {
    @Accessor("value")
    long eap$getECOValue();
}
