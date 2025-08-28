package com.extendedae_plus.mixin.autopattern;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingCalculation.class)
public interface CraftingCalculationAccessor {
    @Accessor("output")
    AEKey extendedae_plus$getOutput();

    @Accessor("requestedAmount")
    long extendedae_plus$getRequestedAmount();
}