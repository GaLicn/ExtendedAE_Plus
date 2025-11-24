package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import com.extendedae_plus.api.smartDoubling.ICraftingCalculationExt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(value = CraftingCalculation.class, remap = false)
public class CraftingCalculationMixin implements ICraftingCalculationExt {
    @Unique private IGrid grid;

    @Inject(method = "<init>",at = @At("RETURN"))
    private void init(Level level, IGrid grid, ICraftingSimulationRequester simRequester, GenericStack output, CalculationStrategy strategy, CallbackInfo ci) {
        this.grid = grid;
    }

    @Override
    public IGrid getGrid() {
        return grid;
    }
}
