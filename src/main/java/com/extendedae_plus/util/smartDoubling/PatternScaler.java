package com.extendedae_plus.util.smartDoubling;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.compat.AdvancedAEScaledPatternBridge;
import com.extendedae_plus.util.ModCheckUtils;

public final class PatternScaler {
    /**
     * AdvancedAE 扩展样板是否可用。
     *
     * <p>AAE 专有的类型只在 {@link AdvancedAEScaledPatternBridge} 中出现，本类不引用其中任何一个。
     * 探测在类初始化时做一次，{@link #createScaled} 之后走普通 Java 调用，不再经过反射。</p>
     */
    private static final boolean ADV_AVAILABLE =
            ModCheckUtils.isAAELoading() && AdvancedAEScaledPatternBridge.isAvailable();

    private PatternScaler() {}

    /**
     * 创建缩放样板。
     * 自动支持原版 AE 和可选 AAE 的 AdvProcessingPattern。
     */
    public static IPatternDetails createScaled(IPatternDetails base, long multiplier) {
        if (base instanceof IMolecularAssemblerSupportedPattern molecularPattern) {
            return new ScaledMolecularAssemblerPattern(molecularPattern, multiplier);
        }

        // Advanced AE 扩展：桥接类返回 null 表示不是 AAE 样板，落到下面的普通样板。
        if (ADV_AVAILABLE) {
            IPatternDetails advPattern = AdvancedAEScaledPatternBridge.tryCreate(base, multiplier);
            if (advPattern != null) {
                return advPattern;
            }
        }

        // 回退原版
        return new ScaledProcessingPattern(base, multiplier);
    }

    /**
     * 计算基于 limit 的最大允许倍率（单次输出主物品 ≤ limit）
     */
    public static int getComputedMul(IPatternDetails proc, int limit) {
        if (limit <= 0) return 0; // 0 = 不限制

        long minMul = Long.MAX_VALUE;

        for (IPatternDetails.IInput input : proc.getInputs()) {
            long amt = input.getMultiplier();
            if (amt <= 0) continue;
            var possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0) continue;
            AEKey key = possible[0].what();
            long unitMul = getUnitMultiplier(key);
            long limitInUnit = (long) limit * unitMul;

            long allowed = limitInUnit / amt;
            allowed = Math.max(1L, allowed);
            minMul = Math.min(minMul, allowed);
        }

        if (minMul == Long.MAX_VALUE) return 0; // 无有效输入 → 不限制
        return minMul > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minMul;
    }

    private static long getUnitMultiplier(AEKey key) {
        if (key instanceof AEItemKey) return 1L;
        if (key instanceof AEFluidKey) return 1000L;

        // Mekanism Chemical 以 mB 计量，与流体一致；按类名判断，不加载可选模组的类型。
        return "me.ramidzkh.mekae2.ae2.MekanismKey".equals(key.getClass().getName()) ? 1000L : 1L;
    }
}
