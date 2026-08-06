package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**适配
 * 修改 PatternProviderLogic.pushPattern 中对 List.contains 的调用，
 * 在遇到缩放样板时回退匹配到原始样板实例。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public class PatternProviderLogicContainsModifyMixin {

    @WrapOperation(method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z")
    )
    @SuppressWarnings("rawtypes,ConstantConditions")
    private boolean eap$patternsContains(List instance, Object o, Operation<Boolean> original) {
        try {
            if (o instanceof ScaledProcessingPattern scaled) {
                IPatternDetails base = scaled.getOriginal();
                if (base != null && original.call(instance, base)) {
                    return true;
                }
            } else if (o instanceof ScaledMolecularAssemblerPattern scaled) {
                IPatternDetails base = scaled.getOriginal();
                if (base != null && original.call(instance, base)) {
                    return true;
                }
            }

            return original.call(instance, o);
        } catch (Throwable t) {
            return original.call(instance, o);
        }
    }
}
