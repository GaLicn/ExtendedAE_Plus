package com.extendedae_plus.util.smartDoubling;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPatternAdv;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import net.minecraftforge.fml.ModList;

public final class PatternScaler {
    private PatternScaler() {
    }

    public static ScaledProcessingPattern scale(AEProcessingPattern base, AEKey target, long requestedAmount) {
        if (base == null) throw new IllegalArgumentException("base");
        if (target == null) throw new IllegalArgumentException("target");

        // 双保险：若样板标记为不允许缩放，直接放弃缩放（返回 null 表示调用方应保持原样板）
        if (base instanceof ISmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
            return null;
        }

        GenericStack[] baseOutputs = base.getOutputs();

        // 新逻辑：不再对样板进行单位化处理
        // 找到目标输出在 outputs 中的索引（尝试匹配 target，否则取第一个非空输出）
        int targetOutIndex = -1;
        for (int i = 0; i < baseOutputs.length; i++) {
            var out = baseOutputs[i];
            if (out != null && target != null && out.what() != null && out.what().equals(target)) {
                targetOutIndex = i;
                break;
            }
        }
        if (targetOutIndex == -1) {
            for (int i = 0; i < baseOutputs.length; i++) {
                if (baseOutputs[i] != null) {
                    targetOutIndex = i;
                    break;
                }
            }
        }
        if (targetOutIndex == -1 && baseOutputs.length > 0) targetOutIndex = 0;

        long perOperationTarget = 1L;
        if (targetOutIndex >= 0 && baseOutputs[targetOutIndex] != null) {
            long amt = baseOutputs[targetOutIndex].amount();
            if (amt > 0) perOperationTarget = amt;
        }

        // 使用最小整数倍（ceil）策略：直接选择满足请求的最小倍数
        long multiplier = 1L;
        if (requestedAmount > 0) {
            long needed = requestedAmount / perOperationTarget + ((requestedAmount % perOperationTarget) == 0 ? 0 : 1);
            multiplier = needed <= 1L ? 1L : needed;
        }

        // 优先应用模式级别的限制（若 base 支持），然后是全局配置
        try {
            int patternLimit = 0;
            if (base instanceof ISmartDoublingAwarePattern aware) {
                patternLimit = aware.eap$getScalingLimit();
            }
            if (patternLimit > 0 && multiplier > patternLimit) {
                multiplier = patternLimit;
            } else {
                int maxMul = ModConfig.INSTANCE.smartScalingMaxMultiplier;
                if (maxMul > 0 && multiplier > maxMul) {
                    multiplier = maxMul;
                }
            }
        } catch (Throwable ignore) {
            // ignore config read errors
        }
        // 小请求绕过：若请求量小且不会带来收益，则不启用缩放（返回 null）
        try {
            int minBenefit = ModConfig.INSTANCE.smartScalingMinBenefitFactor;
            if (minBenefit > 1 && requestedAmount > 0 && requestedAmount < perOperationTarget * (long) minBenefit) {
                return null;
            }
        } catch (Throwable ignore) {
            // 配置读取异常时保持默认行为（不绕过）
        }
        // 应用配置的最大倍数上限（0 表示不限制）
        try {
            int maxMul = ModConfig.INSTANCE.smartScalingMaxMultiplier;
            if (maxMul > 0 && multiplier > maxMul) {
                multiplier = maxMul;
            }
        } catch (Throwable ignore) {
            // 配置读取异常时不施加上限
        }
        if (ModList.get().isLoaded("advanced_ae")) {
            // 如果加载了 Advanced AE 且 base 实现了 AdvPatternDetails，返回兼容版
            try {
                // 软依赖，不直接 import advIface
                Class<?> advIface = Class.forName("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails");
                if (advIface.isInstance(base)) {
                    // 直接 new ScaledProcessingPatternAdv，父类字段会正常初始化
                    return new ScaledProcessingPatternAdv(base, base.getDefinition(), multiplier);
                }
            } catch (Throwable ignore) {
                // 如果 Advanced AE 不存在或反射失败，就忽略，继续走普通逻辑
            }
        }
        // 仅使用 multiplier 构建轻量化 ScaledProcessingPattern（具体视图按需计算）
        return new ScaledProcessingPattern(base, base.getDefinition(), multiplier);
    }
}
