package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.me.service.CraftingService;
import com.extendedae_plus.api.smartDoubling.ICraftingCalculationExt;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import com.google.common.collect.Iterables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

        for (Map.Entry<IPatternDetails, Long> entry : crafts.entrySet()) {
            IPatternDetails processingPattern = entry.getKey();
            long totalAmount = entry.getValue();

            // 非 AEProcessingPattern 直接保留
            if (!(processingPattern instanceof ISmartDoublingAwarePattern aware)) {
                finalCrafts.put(processingPattern, totalAmount);
                continue;
            }

            boolean allowScaling = aware.eap$allowScaling();
            int perCraftLimit = aware.eap$getMultiplierLimit();


            if (!allowScaling || totalAmount <= 1) {
                finalCrafts.put(processingPattern, totalAmount);
                continue;
            }

            if (perCraftLimit <= 0 && ModConfigs.SMART_SCALING_MAX_MULTIPLIER.get() > 0) {
                perCraftLimit = ModConfigs.SMART_SCALING_MAX_MULTIPLIER.get();
            }

            if (perCraftLimit <= 0) {
                // 检查是否开启 provider 轮询分配功能
                if (ModConfigs.PROVIDER_ROUND_ROBIN_ENABLE.getRaw()) {
                    CraftingService craftingService = (CraftingService) ((ICraftingCalculationExt) calculation).getGrid().getCraftingService();
                    int providerCount = Math.max(Iterables.size(craftingService.getProviders(processingPattern)), 1);

                    // totalAmount < providerCount → 只激活 totalAmount 台 provider
                    if (totalAmount < providerCount) {
                        providerCount = (int) totalAmount;
                    }

                    long base = totalAmount / providerCount;
                    long remainder = totalAmount % providerCount;

                    // base+1 组（数量 remainder 个）
                    if (remainder > 0) {
                        IPatternDetails scaledPlus = PatternScaler.createScaled(processingPattern, base + 1);
                        finalCrafts.merge(scaledPlus, remainder, Long::sum);
                    }

                    // base 组（数量 providerCount - remainder 个）
                    long countBase = providerCount - remainder;
                    if (countBase > 0) {
                        IPatternDetails scaledBase = PatternScaler.createScaled(processingPattern, base);
                        finalCrafts.merge(scaledBase, countBase, Long::sum);
                    }
                } else {
                    // 未开启轮询 → 直接分配一次总量
                    IPatternDetails scaled = PatternScaler.createScaled(processingPattern, totalAmount);
                    finalCrafts.put(scaled, 1L);
                }
            } else {
                // 有限制 → 拆分 full + remainder
                long fullCrafts = totalAmount / perCraftLimit;
                long remainder = totalAmount % perCraftLimit;

                if (fullCrafts > 0) {
                    IPatternDetails scaledFull = PatternScaler.createScaled(processingPattern, perCraftLimit);
                    finalCrafts.put(scaledFull, fullCrafts);
                }
                if (remainder > 0) {
                    IPatternDetails scaledRem = PatternScaler.createScaled(processingPattern, remainder);
                    finalCrafts.put(scaledRem, 1L);
                }
            }
        }

        crafts.clear();
        crafts.putAll(finalCrafts);
    }
}