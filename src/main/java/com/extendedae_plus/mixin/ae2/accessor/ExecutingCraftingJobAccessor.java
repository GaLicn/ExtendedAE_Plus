package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {

    @Accessor("tasks")
    Map<IPatternDetails, ExecutingCraftingJobTaskProgressAccessor> extendedae_plus$getTasks();
}
