package com.extendedae_plus.util.smartDoubling;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;

public final class PatternScaler {
    private PatternScaler() {}

    /**
     * 创建缩放样板。
     * <p>如果 multiplier ≤ 1，直接返回原始样板（避免无意义包装）。</p>
     */
    public static IPatternDetails createScaled(AEProcessingPattern original, long multiplier) {
        if (multiplier <= 1) {
            return original; // 关键：demand=1 或 limit=1 → 直接用原样板
        }
        return new ScaledProcessingPattern(original, multiplier);
    }

    /**
     * 计算基于 limit 的最大允许倍率（单次输出主物品 ≤ limit）
     */
    public static int getComputedMul(AEProcessingPattern proc, int limit) {
        if (limit <= 0) return 0; // 0 = 不限制

        long minMul = Long.MAX_VALUE;

        for (var input : proc.getInputs()) {
            long amt = input.getMultiplier();
            if (amt <= 0) continue;

            AEKey key = input.getPossibleInputs()[0].what();
            long unitMultiplier = getUnitMultiplier(key);
            long limitInAEUnit = (long) limit * unitMultiplier;

            long allowedMul = limitInAEUnit / amt;
            allowedMul = Math.max(1, allowedMul); // 至少 1
            minMul = Math.min(minMul, allowedMul);
        }

        return minMul != Long.MAX_VALUE ? (int) Math.min(minMul, Integer.MAX_VALUE) : 0;
    }

    private static long getUnitMultiplier(AEKey key) {
        if (key instanceof AEItemKey) return 1L;
        if (key instanceof AEFluidKey) return 1000L;

        // 支持 Mekanism Chemical 等（反射安全）
        try {
            if ("me.ramidzkh.mekae2.ae2.MekanismKey".equals(key.getClass().getName())) {
                return 1000L;
            }
        } catch (Exception ignored) {}
        return 1L;
    }
}