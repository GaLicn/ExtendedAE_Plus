package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在 CraftingService.getProviders 调用点修正查询用的样板：缩放样板回退到网络注册的原始样板，
 * 读档恢复的裸分子装配样板补查超级矩阵注册的 Strict 包装。
 */
@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceGetProvidersMixin {

    @WrapOperation(method = "getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;",
            at = @At(value = "INVOKE", target = "Lappeng/me/service/helpers/NetworkCraftingProviders;getMediums(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> eap$getMediums(NetworkCraftingProviders instance, IPatternDetails key,
                                                       Operation<Iterable<ICraftingProvider>> original) {
        IPatternDetails base = key;
        if (key instanceof ScaledProcessingPattern scaledProcessingPattern) {
            base = scaledProcessingPattern.getOriginal();
        } else if (key instanceof ScaledMolecularAssemblerPattern scaledMolecularPattern) {
            base = scaledMolecularPattern.getOriginal();
        }

        Iterable<ICraftingProvider> providers = original.call(instance, base);
        if (providers.iterator().hasNext()
                || !(base instanceof IMolecularAssemblerSupportedPattern molecularPattern)
                || base instanceof StrictMolecularAssemblerPattern) {
            return providers;
        }
        // 读档时 AE2 把任务样板解码成裸样板，而超级矩阵以 Strict 包装注册；裸样板 equals 要求同类，
        // HashMap 正向查不到。包装与注册键同哈希且 equals 比较内部样板，补查一次即可命中。
        return original.call(instance, StrictMolecularAssemblerPattern.wrap(molecularPattern));
    }
}
