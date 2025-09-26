package com.extendedae_plus.mixin.advancedae;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**适配
 * Redirect PatternProviderLogic.pushPattern 中对 List.contains 的调用，
 * 在遇到缩放样板时回退匹配到原始样板实例。
 */
@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicContainsRedirectMixin {

    @Redirect(method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z")
    )
    private boolean eap$patternsContains(List<?> list, Object o) {
        try {
            if (o instanceof ScaledProcessingPattern scaled) {
                IPatternDetails base = scaled.getOriginal();
                if (base != null && list.indexOf(base) != -1) {
                    return true;
                }
            }
            // 使用 indexOf 避免再次触发对 List.contains 的 redirect（防止递归）
            return list.indexOf(o) != -1;
        } catch (Throwable t) {
            return list.indexOf(o) != -1;
        }
    }
}