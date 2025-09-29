package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.me.service.CraftingService;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 在 CraftingService.getProviders 调用点修改传入的 IPatternDetails 参数（回退到网络注册的原始样板）
 */
@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceGetProvidersMixin {

    @ModifyArg(method = "getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;",
            at = @At(value = "INVOKE", target = "Lappeng/me/service/helpers/NetworkCraftingProviders;getMediums(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"),
            index = 0)
    private IPatternDetails eap$modifyGetProvidersArg(IPatternDetails original) {
        IPatternDetails base = null;
        if (original instanceof ScaledProcessingPattern scaledProcessingPattern) {
            base = scaledProcessingPattern.getOriginal();
        }
        return base == null ? original : base;
    }
}


