package com.extendedae_plus.util;

import com.extendedae_plus.config.ModConfigs;

/**
 * 用于计算实体加速器的能耗与加速倍率的工具类
 */
public final class PowerUtils {
    private PowerUtils() {}

    /**
     * 计算加速卡数量对应的加速倍率（返回 2 的幂次方）
     * 0 张卡 = 1x，8 张卡 = 1024x
     */
    public static int getSpeedMultiplier(int speedCardCount) {
        if (speedCardCount <= 0) return 1;
        // 线性映射 0~8 -> 0~10，最大 2^10=1024
        int exponent = (int) Math.round((10.0 / 8.0) * speedCardCount);
        exponent = Math.min(exponent, 10);
        return (int) Math.pow(2, exponent);
    }

    /**
     * 计算加速卡数量对应的能耗增长因子
     * 0: 1x，1: 2x，2-6: 2^(2*count)，7-8: 2^(3*count)
     */
    public static double getGrowthFactor(int speedCardCount) {
        if (speedCardCount <= 0) return 1.0;
        if (speedCardCount == 1) return 2.0;
        if (speedCardCount <= 6) return Math.pow(2.0, 2.0 * speedCardCount);
        return Math.pow(2.0, 3.0 * speedCardCount);
    }

    /**
     * 计算加速卡数量对应的原始能耗（未减免）
     * @param speedCardCount 加速卡数量
     * @return 原始能耗
     */
    public static double getRawPower(int speedCardCount) {
        double base = ModConfigs.EntitySpeedTickerCost.get();
        return base * getGrowthFactor(speedCardCount);
    }

    /**
     * 计算能源卡数量对应的能耗减免百分比
     * 0: 0%，1: 10%，8: 50%，2-7: 0.5*(1-0.7^n)
     */
    public static double getReductionPercent(int energyCardCount) {
        if (energyCardCount <= 0) return 0.0;
        if (energyCardCount == 1) return 0.1;
        if (energyCardCount >= 8) return 0.5;
        return 0.5 * (1.0 - Math.pow(0.7, energyCardCount));
    }

    /**
     * 计算最终能耗（浮点数）
     * @param speedCardCount 加速卡数量
     * @param energyCardCount 能源卡数量
     * @return 最终能耗（double）
     */
    public static double getFinalPower(int speedCardCount, int energyCardCount) {
        double raw = getRawPower(speedCardCount);
        double reduction = getReductionPercent(energyCardCount);
        // 返回与实际抽取值一致的浮点能耗（在原实现基础上再除以 2，以降低能耗）
        return raw * (1.0 - reduction) / 4.0;
    }

    /**
     * 返回能源卡减免后剩余的功耗比率（例如 1 张能源卡 -> 0.9）
     * @param energyCardCount 能源卡数量
     * @return 剩余功耗比率
     */
    public static double getRemainingRatio(int energyCardCount) {
        return 1.0 - getReductionPercent(energyCardCount);
    }

    /**
     * 将剩余功耗比率格式化为百分比字符串（例如 0.9 -> "90%"）
     */
    public static String formatPercentage(double ratio) {
        double pct = ratio * 100.0;
        // 如果为整数则无小数
        if (Math.abs(pct - Math.round(pct)) < 1e-9) {
            return String.format("%d%%", Math.round(pct));
        }
        return String.format("%.2f%%", pct);
    }
}
