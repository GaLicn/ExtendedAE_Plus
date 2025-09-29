package com.extendedae_plus.mixin.ae2;

import appeng.crafting.CraftingCalculation;
import com.extendedae_plus.config.EAEPConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Mixin 用于增加 CraftingCalculation.handlePausing() 中 incTime 的阈值。
 * 调整 CRAFTING_PAUSE_THRESHOLD 可以控制计算让出控制权的频率。
 */
@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationMixin {
    // 可调节的阈值：在执行 pause 检查前 handlePausing 被调用的次数。
    // 增大此值可以减少 wait/notify 的频率（提升吞吐量但降低响应速度）。
    @ModifyConstant(method = "handlePausing", constant = @Constant(intValue = 100))
    private int modifyIncTimeThreshold(int original) {
        return EAEPConfig.CRAFTING_PAUSE_THRESHOLD.get();
    }
}