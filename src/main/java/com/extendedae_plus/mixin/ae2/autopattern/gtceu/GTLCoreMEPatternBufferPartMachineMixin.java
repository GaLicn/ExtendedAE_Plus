package com.extendedae_plus.mixin.ae2.autopattern.gtceu;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.SmartDoublingAwarePattern;
import com.extendedae_plus.content.ScaledProcessingPattern;
import com.google.common.collect.BiMap;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

@Pseudo
@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public class GTLCoreMEPatternBufferPartMachineMixin {

    @Final
    @Shadow
    private BiMap<IPatternDetails, Integer> patternSlotMap;

    // 设置样板总成是否翻倍
    @Inject(method = "getAvailablePatterns", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toList()Ljava/util/List;", shift = At.Shift.BEFORE))
    private void beforeToList(CallbackInfoReturnable<List<IPatternDetails>> cir) {
        if (patternSlotMap == null) return;
        for (Map.Entry<IPatternDetails, Integer> entry : patternSlotMap.entrySet()) {
            IPatternDetails key = entry.getKey();
            if (key instanceof AEProcessingPattern proc && proc instanceof SmartDoublingAwarePattern aware) {
                aware.eap$setAllowScaling(true);
                LOGGER.info("设置true，{}", aware);
            }
        }
    }

    // 重定向containsKey检查
    @Redirect(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
              at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;containsKey(Ljava/lang/Object;)Z"))
    private boolean redirectContainsKey(BiMap<IPatternDetails, ?> detailsSlotMap, Object key) {
        try {
            // 如果key是ScaledProcessingPattern类型，尝试用其原始pattern进行判断
            if (key instanceof ScaledProcessingPattern scaled) {
                IPatternDetails base = scaled.getOriginal();
                if (base != null) {
                    // 避免递归重定向，直接遍历keySet判断
                    for (IPatternDetails d : detailsSlotMap.keySet()) {
                        if (Objects.equals(d, base)) return true;
                    }
                }
            }
            // 常规判断，遍历keySet
            for (IPatternDetails d : detailsSlotMap.keySet()) {
                if (Objects.equals(d, key)) return true;
            }
            return false;
        } catch (Throwable t) {
            // 出现异常时，回退到常规判断
            for (IPatternDetails d : detailsSlotMap.keySet()) {
                if (Objects.equals(d, key)) return true;
            }
            return false;
        }
    }

    @Redirect(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
              at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object redirectGet(BiMap<IPatternDetails, ?> detailsSlotMap, Object key) {
        try {
            // 如果是 ScaledProcessingPattern，优先尝试其原始 pattern 对应的值
            if (key instanceof ScaledProcessingPattern scaled) {
                IPatternDetails base = scaled.getOriginal();
                if (base != null) {
                    for (Map.Entry<IPatternDetails, ?> e : detailsSlotMap.entrySet()) {
                        if (Objects.equals(e.getKey(), base)) {
                            return e.getValue();
                        }
                    }
                }
            }

            // 常规查找：遍历 entrySet 避免再次调用 BiMap.get 导致递归重定向
            for (Map.Entry<IPatternDetails, ?> e : detailsSlotMap.entrySet()) {
                if (Objects.equals(e.getKey(), key)) {
                    return e.getValue();
                }
            }
            return null;
        } catch (Throwable t) {
            for (Map.Entry<IPatternDetails, ?> e : detailsSlotMap.entrySet()) {
                if (Objects.equals(e.getKey(), key)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }
}


