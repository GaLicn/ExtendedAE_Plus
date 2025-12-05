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
    Map<IPatternDetails, ExecutingCraftingJobTaskProgressAccessor> extendedae_plus$getTasks();

    @Accessor("playerId")
    Integer extendedae_plus$getPlayerId();

    @Accessor("finalOutput")
    GenericStack extendedae_plus$getFinalOutput();

    @Accessor("remainingAmount")
    long extendedae_plus$getRemainingAmount();

    @Accessor("link")
    CraftingLink extendedae_plus$getLink();
}
