package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.service.CraftingService;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ICraftingSimulationStateExt;
import com.extendedae_plus.api.smartDoubling.ICraftingTreeProcessExt;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import com.google.common.collect.Iterables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateMixin implements ICraftingSimulationStateExt {
    @Unique private CraftingTreeProcess sourceProcess;

    /**
     * 替换 CraftingPlan 构建逻辑，在此统一处理样板倍率
     */
    @Inject(method = "buildCraftingPlan", at = @At("HEAD"))
    private static void onBuildCraftingPlan(CraftingSimulationState state, CraftingCalculation calculation, long calculatedAmount, CallbackInfoReturnable<CraftingPlan> cir) {
        CraftingSimulationStateAccessor accessor = (CraftingSimulationStateAccessor) state;
        Map<IPatternDetails, Long> crafts = accessor.getCrafts();
        // 新建 Map 存放最终分配后的 crafts
        Map<IPatternDetails, Long> finalCrafts = new LinkedHashMap<>();

        for (Map.Entry<IPatternDetails, Long> entry : crafts.entrySet()) {
            IPatternDetails details = entry.getKey();
            long totalAmount = entry.getValue();

            // 非 AEProcessingPattern 直接保留
            if (!(details instanceof AEProcessingPattern processingPattern)) {
                finalCrafts.put(details, totalAmount);
                continue;
            }

            boolean allowScaling = false;
            int perCraftLimit = 0;
            if (processingPattern instanceof ISmartDoublingAwarePattern aware) {
                allowScaling = aware.eap$allowScaling();
                perCraftLimit = aware.eap$getMultiplierLimit();
            }

            if (!allowScaling || totalAmount <= 1) {
                finalCrafts.put(processingPattern, totalAmount);
                continue;
            }

            if (perCraftLimit <= 0 && ModConfig.INSTANCE.smartScalingMaxMultiplier > 0) {
                perCraftLimit = ModConfig.INSTANCE.smartScalingMaxMultiplier;
            }

            // 获取供应器数量
            CraftingTreeProcess process = ((ICraftingSimulationStateExt) state).getSourceProcess();
            CraftingService craftingService = (CraftingService) ((ICraftingTreeProcessExt) process).getCraftingService();
            long providerCount = Iterables.size(craftingService.getProviders(processingPattern));
            if (providerCount <= 0) providerCount = 1;

            if (perCraftLimit <= 0) {
                // 检查是否开启 provider 轮询分配功能
                if (ModConfig.INSTANCE.providerRoundRobinEnable) {
                    long base = totalAmount / providerCount;
                    long remainder = totalAmount % providerCount;

                    // base+1 组（数量 remainder 个）
                    if (remainder > 0) {
                        ScaledProcessingPattern scaledPlus = PatternScaler.createScaled(processingPattern, base + 1);
                        finalCrafts.merge(scaledPlus, remainder, Long::sum);
                    }

                    // base 组（数量 providerCount - remainder 个）
                    long countBase = providerCount - remainder;
                    if (countBase > 0) {
                        ScaledProcessingPattern scaledBase = PatternScaler.createScaled(processingPattern, base);
                        finalCrafts.merge(scaledBase, countBase, Long::sum);
                    }
                } else {
                    // 未开启轮询 → 直接分配一次总量
                    ScaledProcessingPattern scaled = PatternScaler.createScaled(processingPattern, totalAmount);
                    finalCrafts.put(scaled, 1L);
                }
            } else {
                // 有限制 → 拆分 full + remainder
                long fullCrafts = totalAmount / perCraftLimit;
                long remainder = totalAmount % perCraftLimit;

                if (fullCrafts > 0) {
                    ScaledProcessingPattern scaledFull = PatternScaler.createScaled(processingPattern, perCraftLimit);
                    finalCrafts.put(scaledFull, fullCrafts);
                }
                if (remainder > 0) {
                    ScaledProcessingPattern scaledRem = PatternScaler.createScaled(processingPattern, remainder);
                    finalCrafts.put(scaledRem, 1L);
                }
            }
        }

        crafts.clear();
        crafts.putAll(finalCrafts);
    }

    @Override
    public CraftingTreeProcess getSourceProcess() {
        return this.sourceProcess;
    }

    @Override
    public void setSourceProcess(CraftingTreeProcess process) {
        this.sourceProcess = process;
    }
}
