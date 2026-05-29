package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ExecutingCraftingJobTaskProgressAccessor> eap$getTasks();

    @Accessor("finalOutput")
    GenericStack eap$getFinalOutput();

    @Accessor("remainingAmount")
    long eap$getRemainingAmount();

    @Accessor("remainingAmount")
    void eap$setRemainingAmount(long remainingAmount);

    @Accessor("link")
    CraftingLink eap$getLink();
}
