package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 使 GTLCore 的 CPU 供应器查询兼容 EAEP 已缩放的合成批次。
 */
@Mixin(value = TransfiniteCraftingLogic.class,
        priority = 4000, remap = false)
public abstract class TransfiniteCraftingSmartDoublingCompatMixin {

    private static final String GET_PROVIDERS =
            "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)"
                    + "Ljava/lang/Iterable;";

    @WrapOperation(
            method = "executeCrafting(JLappeng/me/service/CraftingService;"
                    + "Lappeng/api/networking/energy/IEnergyService;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lorg/gtlcore/gtlcore/integration/ae2/crafting/"
                    + "CraftingDispatchPerformanceLogger$Metrics;)J",
            at = @At(value = "INVOKE", target = GET_PROVIDERS, remap = false),
            require = 0
    )
    private Iterable<ICraftingProvider> eap$useOriginalPatternForProviderLookup(
            CraftingService craftingService,
            IPatternDetails pattern,
            Operation<Iterable<ICraftingProvider>> original) {
        return original.call(craftingService, EapScaledPatternCompat.unwrap(pattern));
    }

}
