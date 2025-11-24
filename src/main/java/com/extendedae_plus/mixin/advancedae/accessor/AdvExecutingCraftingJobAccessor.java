package com.extendedae_plus.mixin.advancedae.accessor;

import appeng.api.crafting.IPatternDetails;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface AdvExecutingCraftingJobAccessor {

    @Accessor("tasks")
    Map<IPatternDetails, Object> eap$getAdvTasks();
}
