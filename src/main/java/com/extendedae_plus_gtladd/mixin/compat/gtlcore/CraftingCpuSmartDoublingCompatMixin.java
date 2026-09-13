package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 使 GTLCore 的供应器查询兼容 EAEP 已缩放的合成批次。
 *
 * <p>MixinSquared 直接定位 GTLCore 的覆盖方法。若直接注入
 * {@code CraftingCpuLogic.executeCrafting}，该注入会被覆盖方法丢弃。</p>
 */
@Mixin(value = CraftingCpuLogic.class, priority = 4000, remap = false)
public abstract class CraftingCpuSmartDoublingCompatMixin {

    private static final String GET_PROVIDERS =
            "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)"
                    + "Ljava/lang/Iterable;";

    // 样板是该调用的参数，因此无需查找局部变量。
    @TargetHandler(
            mixin = "org.gtlcore.gtlcore.mixin.ae2.logic.CraftingCpuLogicMixin",
            name = "executeCrafting"
    )
    @WrapOperation(
            method = "@MixinSquared:Handler",
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
