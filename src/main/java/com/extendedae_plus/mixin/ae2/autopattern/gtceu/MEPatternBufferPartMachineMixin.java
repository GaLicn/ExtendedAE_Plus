package com.extendedae_plus.mixin.ae2.autopattern.gtceu;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.content.ScaledProcessingPattern;
import com.google.common.collect.BiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Objects;

;

@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine",remap = false)
public class MEPatternBufferPartMachineMixin {

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


