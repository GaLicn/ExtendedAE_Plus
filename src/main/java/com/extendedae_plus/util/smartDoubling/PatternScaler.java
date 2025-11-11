package com.extendedae_plus.util.smartDoubling;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import net.minecraftforge.fml.loading.LoadingModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class PatternScaler {
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
            ctor = clazz.getConstructor(AEProcessingPattern.class, long.class);

            // 加载接口
            iface = Class.forName("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails");

            // 检查是否安装 Advanced AE
            if (LoadingModList.get() != null && LoadingModList.get().getModFileById("advanced_ae") != null) {
                available = true;
            }
        } catch (Throwable ignored) {}

        advAvailable = available;
        advCtor = ctor;
        advIfaceClass = iface;
    }

    private PatternScaler() {}

    /**
     * 创建缩放样板。
     * 自动支持原版 AE 和可选 AAE 的 AdvProcessingPattern。
     */
    public static ScaledProcessingPattern createScaled(AEProcessingPattern base, long multiplier) {
        // 尝试 Advanced AE 扩展
        if (advAvailable && advIfaceClass != null && advCtor != null) {
            try {
                if (advIfaceClass.isInstance(base)) {
                    return (ScaledProcessingPattern) advCtor.newInstance(base, multiplier);
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
                // 出错退回普通逻辑
            }
        }

        // 回退原版
        return new ScaledProcessingPattern(base, multiplier);
    }

    /**
     * 计算基于 limit 的最大允许倍率（单次输出主物品 ≤ limit）
     */
    public static int getComputedMul(AEProcessingPattern proc, int limit) {
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

        // 支持 Mekanism Chemical 等（反射安全）
        try {
            if ("me.ramidzkh.mekae2.ae2.MekanismKey".equals(key.getClass().getName())) {
                return 1000L;
            }
        } catch (Exception ignored) {}
        return 1L;
    }
}