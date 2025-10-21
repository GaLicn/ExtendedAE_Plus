package com.extendedae_plus.util.smartDoubling;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import net.minecraftforge.fml.loading.LoadingModList;

import java.lang.reflect.Constructor;

public final class PatternScaler {
    // ---------- 静态缓存反射 ----------
    private static final boolean advAvailable;
    private static final Constructor<?> advCtor;
    private static final Class<?> advIfaceClass;

    static {
        boolean available = false;
        Constructor<?> ctor = null;
        Class<?> iface = null;

        try {
            // 尝试加载扩展类
            Class<?> clazz = Class.forName("com.extendedae_plus.ae.api.crafting.ScaledProcessingPatternAdv");
            ctor = clazz.getConstructor(AEProcessingPattern.class, AEItemKey.class, long.class);

            // 加载接口
            iface = Class.forName("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails");

            // 检查是否安装 Advanced AE
            if (LoadingModList.get() != null && LoadingModList.get().getModFileById("advanced_ae") != null) {
                available = true;
            }
        } catch (Throwable ignored) {
        }

        advAvailable = available;
        advCtor = ctor;
        advIfaceClass = iface;
    }

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

        long multiplier = 1L; // 默认倍数

        // ---------------------- 优先模式限制 ----------------------
        boolean patternHasLimit = false;
        if (base instanceof ISmartDoublingAwarePattern aware) {
            int patternMulLimit = aware.eap$getMultiplierLimit();
            if (patternMulLimit > 0) {
                multiplier = patternMulLimit; // 直接使用模式限制作为倍数
                patternHasLimit = true;
            }
        }

        // ---------------------- 全局逻辑（仅在没有模式限制时生效） ----------------------
        if (!patternHasLimit && requestedAmount > 0) {
            // 计算满足请求量的最小倍数
            long needed = requestedAmount / perOperationTarget + ((requestedAmount % perOperationTarget) == 0 ? 0 : 1);
            multiplier = Math.max(needed, 1L);

            // 应用全局上限
            int maxMul = ModConfig.INSTANCE.smartScalingMaxMultiplier;
            if (maxMul > 0 && multiplier > maxMul) multiplier = maxMul;
        }

        // ---------------------- 小请求绕过 ----------------------
        try {
            int minBenefit = ModConfig.INSTANCE.smartScalingMinBenefitFactor;
            if (minBenefit > 1 && requestedAmount > 0 && requestedAmount < perOperationTarget * (long) minBenefit) {
                return null;
            }
        } catch (Throwable ignore) {}

        // ---------- Advanced AE 扩展 ----------
        if (advAvailable && advIfaceClass != null && advCtor != null) {
            try {
                if (advIfaceClass.isInstance(base)) {
                    return (ScaledProcessingPattern) advCtor.newInstance(base, base.getDefinition(), multiplier);
                }
            } catch (Throwable ignore) {
                // 出错就退回普通逻辑
            }
        }

        // 仅使用 multiplier 构建轻量化 ScaledProcessingPattern（具体视图按需计算）
        return new ScaledProcessingPattern(base, base.getDefinition(), multiplier);
    }

    public static int getComputedMul(AEProcessingPattern proc, int limit) {
        int computedMul = 0; // 默认 0 表示不限制

        if (limit > 0) {
            long minMul = Long.MAX_VALUE;

            for (var input : proc.getInputs()) {
                long amt = input.getMultiplier();
                if (amt <= 0) continue;

                AEKey key = input.getPossibleInputs()[0].what();

                // 使用统一单位换算
                long unitMultiplier = getUnitMultiplier(key);
                long limitInAEUnit = limit * unitMultiplier;

                // 计算该输入允许的倍数
                long allowedMul = limitInAEUnit / amt;

                // 保证至少为 1
                allowedMul = Math.max(1, allowedMul);

                // 取最小值，保证所有输入都不超过 limit
                minMul = Math.min(minMul, allowedMul);
            }

            if (minMul != Long.MAX_VALUE) {
                computedMul = (int) Math.min(minMul, Integer.MAX_VALUE);
            }
        }

        return computedMul;
    }

    /**
     * 获取 AEKey 的单位换算系数
     * 物品默认 1，流体默认 1000，其它类型可通过扩展接口提供
     */
    private static long getUnitMultiplier(AEKey key) {
        if (key instanceof AEItemKey) return 1L;
        if (key instanceof AEFluidKey) return 1000L;

        try {
            // 反射判断扩展 Key 类型
            if (key.getClass().getName().equals("me.ramidzkh.mekae2.ae2.MekanismKey")) {
                return 1000L;
            }
            // 根据需要继续增加
        } catch (Exception ignored) {}

        return 1L; // 默认单位
    }
}
