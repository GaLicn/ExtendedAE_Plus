package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateMixin {

    @Shadow private Map<IPatternDetails, Long> crafts;

    /** 仅用于无限制缩放时的合并缓存 */
    private final Map<AEProcessingPattern, ScaledProcessingPattern> scaledCache = new IdentityHashMap<>();

    @Inject(method = "addCrafting", at = @At("HEAD"), cancellable = true)
    private void onAddCrafting(IPatternDetails details, long craftsAmount, CallbackInfo ci) {
        ci.cancel();
        if (craftsAmount <= 0 || details == null) return;

        if (details instanceof AEProcessingPattern processingPattern) {
            boolean allowScaling = true;
            int perCraftLimit = 0;

            if (processingPattern instanceof ISmartDoublingAwarePattern aware) {
                allowScaling = aware.eap$allowScaling();
                perCraftLimit = aware.eap$getMultiplierLimit();
            }

            if (!allowScaling) {
                crafts.merge(processingPattern, craftsAmount, Long::sum);
                return;
            }

            // === 需求为 1 → 直接用原样板 ===
            if (craftsAmount == 1) {
                crafts.merge(processingPattern, 1L, Long::sum);
                return;
            }

            // === 计算实际限制 ===
            if (perCraftLimit > 0) {
                perCraftLimit = Math.min(perCraftLimit, PatternScaler.getComputedMul(processingPattern, perCraftLimit));
            }

            if (perCraftLimit <= 0) {
                // 无限制 → 合并缩放
                mergeUnlimited(processingPattern, craftsAmount);
            } else {
                // 有限制 → 拆分
                splitLimited(processingPattern, craftsAmount, perCraftLimit);
            }

        } else {
            crafts.merge(details, craftsAmount, Long::sum);
        }
    }

    /** 无限制：合并倍率 */
    private void mergeUnlimited(AEProcessingPattern original, long multiplier) {
        ScaledProcessingPattern existing = scaledCache.get(original);
        long total = multiplier;

        if (existing != null) {
            total += existing.getMultiplier();
            crafts.remove(existing);
        }

        IPatternDetails scaled = PatternScaler.createScaled(original, total);
        if (scaled instanceof ScaledProcessingPattern sp) {
            scaledCache.put(original, sp);
            crafts.put(sp, 1L);
        } else {
            crafts.put(original, total); // 退化为原样板
        }
    }

    /** 有限制：拆分 full + remainder */
    private void splitLimited(AEProcessingPattern original, long totalAmount, int limit) {
        long fullCrafts = totalAmount / limit;
        long remainder = totalAmount % limit;

        if (fullCrafts > 0) {
            IPatternDetails scaled = PatternScaler.createScaled(original, limit);
            crafts.merge(scaled, fullCrafts, Long::sum);
        }

        if (remainder > 0) {
            IPatternDetails scaled = PatternScaler.createScaled(original, remainder);
            crafts.merge(scaled, 1L, Long::sum);
        }
    }
}