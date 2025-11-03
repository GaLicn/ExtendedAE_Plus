package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

@SuppressWarnings({"AddedMixinMembersNamePattern", "MissingUnique"})
@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateMixin {

    @Shadow @Final private Map<IPatternDetails, Long> crafts;

    /** 仅用于无限制缩放时的合并缓存 */
    private final Map<AEProcessingPattern, ScaledProcessingPattern> scaledCache = new IdentityHashMap<>();

    @Inject(method = "addCrafting", at = @At("HEAD"), cancellable = true)
    private void onAddCrafting(IPatternDetails details, long craftsAmount, CallbackInfo ci) {
        ci.cancel();
        if (craftsAmount <= 0 || details == null) return;

        if (!(details instanceof AEProcessingPattern processingPattern)) {
            crafts.merge(details, craftsAmount, Long::sum);
            return;
        }

        boolean allowScaling = false;
        int perCraftLimit = 0;

        if (processingPattern instanceof ISmartDoublingAwarePattern aware) {
            allowScaling = aware.eap$allowScaling();
            perCraftLimit = aware.eap$getMultiplierLimit(); // 已经是最大倍率限制
        }

        // 不允许缩放或者需求为 1
        if (!allowScaling || craftsAmount == 1) {
            crafts.merge(processingPattern, craftsAmount, Long::sum);
            return;
        }

        if (perCraftLimit <= 0) {
            // 无限制 → 合并倍率并复用对象
            mergeUnlimited(processingPattern, craftsAmount);
        } else {
            // 有限制 → 拆分 full + remainder
            splitLimited(processingPattern, craftsAmount, perCraftLimit);
        }
    }

    /** 无限制：合并倍率并复用 ScaledProcessingPattern 或 AAE 扩展 */
    private void mergeUnlimited(AEProcessingPattern original, long multiplier) {
        ScaledProcessingPattern existing = scaledCache.get(original);
        long total = multiplier;

        if (existing != null) {
            total += existing.getMultiplier();
            crafts.remove(existing);
        }

        // 使用 PatternScaler 自动选择原版或 AAE 扩展
        ScaledProcessingPattern scaled = PatternScaler.createScaled(original, total);

        scaledCache.put(original, scaled);
        crafts.put(scaled, 1L);
    }

    /** 有限制：拆分 full + remainder，并支持原版/AAE 扩展 */
    private void splitLimited(AEProcessingPattern original, long totalAmount, int limit) {
        long fullCrafts = totalAmount / limit;
        long remainder = totalAmount % limit;

        if (fullCrafts > 0) {
            ScaledProcessingPattern scaledFull = PatternScaler.createScaled(original, limit);
            crafts.merge(scaledFull, fullCrafts, Long::sum);
        }

        if (remainder > 0) {
            ScaledProcessingPattern scaledRemainder = PatternScaler.createScaled(original, remainder);
            crafts.merge(scaledRemainder, 1L, Long::sum);
        }
    }
}