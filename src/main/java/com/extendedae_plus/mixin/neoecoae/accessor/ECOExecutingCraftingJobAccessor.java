package com.extendedae_plus.mixin.neoecoae.accessor;

// NeoECOAE 适配暂时禁用，待发布适配版本后恢复；build.gradle 已排除本文件。

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ECOExecutingCraftingJobAccessor {

    @Accessor("tasks")
    Map<IPatternDetails, Object> eap$getECOTasks();
}
