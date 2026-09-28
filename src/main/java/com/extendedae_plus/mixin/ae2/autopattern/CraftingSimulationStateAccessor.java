package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.CraftingSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CraftingSimulationState.class,remap = false)
public interface CraftingSimulationStateAccessor {
    @Accessor("crafts")
    Map<IPatternDetails, Long> getCrafts();
}
