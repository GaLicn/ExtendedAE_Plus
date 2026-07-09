package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.me.service.CraftingService;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ICraftingCalculationExt;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateMixin {
    /**
     * 替换 CraftingPlan 构建逻辑，在此统一处理样板倍率
     */
    @Inject(method = "buildCraftingPlan", at = @At("HEAD"))
    private static void onBuildCraftingPlan(CraftingSimulationState state,
                                            CraftingCalculation calculation,
                                            long calculatedAmount,
                                            CallbackInfoReturnable<CraftingPlan> cir) {
        CraftingSimulationStateAccessor accessor = (CraftingSimulationStateAccessor) state;
        Map<IPatternDetails, Long> crafts = accessor.getCrafts();
        // 存放最终分配后的 crafts
        Map<IPatternDetails, Long> finalCrafts = new LinkedHashMap<>();
        Map<IPatternDetails, Integer> providerCountCache = new HashMap<>();

        for (Map.Entry<IPatternDetails, Long> entry : crafts.entrySet()) {
            IPatternDetails details = entry.getKey();
            long totalAmount = entry.getValue();

            // 只有声明支持智能倍增的样板才参与缩放，避免影响其它 provider。
            if (!(details instanceof ISmartDoublingAwarePattern aware)) {
                finalCrafts.put(details, totalAmount);
                continue;
            }

            boolean allowScaling = aware.eap$allowScaling();
            int perCraftLimit = aware.eap$getMultiplierLimit();

            if (!allowScaling || totalAmount <= 1) {
                finalCrafts.put(details, totalAmount);
                continue;
            }

            if (perCraftLimit <= 0 && ModConfig.INSTANCE.smartScalingMaxMultiplier > 0) {
                perCraftLimit = ModConfig.INSTANCE.smartScalingMaxMultiplier;
            }

            if (perCraftLimit <= 0) {
                // 检查是否开启 provider 轮询分配功能
                if (ModConfig.INSTANCE.providerRoundRobinEnable) {
                    CraftingService craftingService = (CraftingService) ((ICraftingCalculationExt) calculation).getGrid().getCraftingService();
                    int providerCount = Math.max(
                            providerCountCache.computeIfAbsent(
                                    getProviderCacheKey(details),
                                    key -> countProvidersUpTo(craftingService.getProviders(key), totalAmount)),
                            1);

                    // totalAmount < providerCount → 只激活 totalAmount 台 provider
                    if (totalAmount < providerCount) {
                        providerCount = (int) totalAmount;
                    }

                    long base = totalAmount / providerCount;
                    long remainder = totalAmount % providerCount;

                    // base+1 组（数量 remainder 个）
                    if (remainder > 0) {
                        IPatternDetails scaledPlus = PatternScaler.createScaled(details, base + 1);
                        finalCrafts.merge(scaledPlus, remainder, Long::sum);
                    }

                    // base 组（数量 providerCount - remainder 个）
                    long countBase = providerCount - remainder;
                    if (countBase > 0) {
                        IPatternDetails scaledBase = PatternScaler.createScaled(details, base);
                        finalCrafts.merge(scaledBase, countBase, Long::sum);
                    }
                } else {
                    // 未开启轮询 → 直接分配一次总量
                    IPatternDetails scaled = PatternScaler.createScaled(details, totalAmount);
                    finalCrafts.put(scaled, 1L);
                }
            } else {
                // 有限制 → 拆分 full + remainder
                long fullCrafts = totalAmount / perCraftLimit;
                long remainder = totalAmount % perCraftLimit;

                if (fullCrafts > 0) {
                    IPatternDetails scaledFull = PatternScaler.createScaled(details, perCraftLimit);
                    finalCrafts.put(scaledFull, fullCrafts);
                }
                if (remainder > 0) {
                    IPatternDetails scaledRem = PatternScaler.createScaled(details, remainder);
                    finalCrafts.put(scaledRem, 1L);
                }
            }
        }

        crafts.clear();
        crafts.putAll(finalCrafts);
    }

    private static IPatternDetails getProviderCacheKey(IPatternDetails pattern) {
        if (pattern instanceof ScaledProcessingPattern scaled) {
            return scaled.getOriginal();
        }
        if (pattern instanceof ScaledMolecularAssemblerPattern scaled) {
            return scaled.getOriginal();
        }
        return pattern;
    }

    private static int countProvidersUpTo(Iterable<ICraftingProvider> providers, long maxNeeded) {
        int count = 0;
        int limit = maxNeeded <= 0
                ? Integer.MAX_VALUE
                : maxNeeded >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxNeeded;

        for (var ignored : providers) {
            count++;
            if (count >= limit) {
                break;
            }
        }

        return count;
    }
}
