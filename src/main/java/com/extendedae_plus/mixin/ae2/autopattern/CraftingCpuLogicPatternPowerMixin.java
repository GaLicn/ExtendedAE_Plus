package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuLogic;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 超级矩阵的倍率样板按单次合成检查和扣除 AE 能量，避免整批能耗超过网络缓存。 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicPatternPowerMixin {

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
