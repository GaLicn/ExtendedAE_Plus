package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 使 GTLCore 的 CPU 供应器查询兼容 EAEP 已缩放的合成批次。
 */
@Mixin(value = TransfiniteCraftingLogic.class,
        priority = 4000, remap = false)
public abstract class TransfiniteCraftingSmartDoublingCompatMixin {

    @Unique private static final String GET_PROVIDERS =
            "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)"
                    + "Ljava/lang/Iterable;";

    /**
     * 不带描述符，只按方法名匹配：fix6 及更早的签名末尾多一个
     * CraftingDispatchPerformanceLogger.Metrics 参数，fix7 起该参数被移除，
     * 写死描述符会让注解处理器在另一个版本上报找不到目标方法。
     */
    @Unique private static final String EXECUTE_CRAFTING = "executeCrafting";

    @WrapOperation(
            method = EXECUTE_CRAFTING,
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
