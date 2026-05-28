package com.extendedae_plus.mixin.neoecoae.accessor;

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
