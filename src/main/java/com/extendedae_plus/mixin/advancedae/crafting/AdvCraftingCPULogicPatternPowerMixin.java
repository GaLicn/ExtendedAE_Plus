package com.extendedae_plus.mixin.advancedae.crafting;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 让 AdvanceAE 量子计算机的超级矩阵倍率样板按单次合成检查和扣除 AE 能量。 */
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvCraftingCPULogicPatternPowerMixin {

    @ModifyExpressionValue(method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuHelper;calculatePatternPower([Lappeng/api/stacks/KeyCounter;)D"))
    private static double eap$useSingleCraftPower(double original, @Local IPatternDetails details) {
        if (details instanceof ScaledMolecularAssemblerPattern scaled
                && scaled.getOriginal() instanceof StrictMolecularAssemblerPattern
                && scaled.getMultiplier() > 1) {
            return original / scaled.getMultiplier();
        }
        return original;
    }
}
