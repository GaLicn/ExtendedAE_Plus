package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus_gtladd.util.EapScaledPatternCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 使用原始样板身份进行中继路由查询，同时将缩放包装器传给选中的供应器。
 */
@Mixin(value = PatternRelayPart.class, priority = 4000, remap = false)
public abstract class GtlcorePatternRelayCompatMixin {

    @WrapOperation(
            method = {"pushPattern", "gtlcore$getMaxPatternOperations"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            require = 0
    )
    private Object eap$useOriginalPatternForRouteLookup(
            java.util.Map<?, ?> routes,
            Object pattern,
            Operation<Object> original) {
        if (pattern instanceof IPatternDetails details) {
            pattern = EapScaledPatternCompat.unwrap(details);
        }
        return original.call(routes, pattern);
    }
}
