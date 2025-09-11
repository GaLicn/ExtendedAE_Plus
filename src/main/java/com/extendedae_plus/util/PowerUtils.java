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
     * 计算最终能耗（取整，最小为 1）
     * @param speedCardCount 加速卡数量
     * @param energyCardCount 能源卡数量
     * @return 最终能耗（int）
     */
    public static int getFinalPowerDraw(int speedCardCount, int energyCardCount) {
        double raw = getRawPower(speedCardCount);
        double reduction = getReductionPercent(energyCardCount);
        // 与原始实现兼容：raw * (1 - reduction) 后按 0.5 的单位转换（与 AE/FE 换算保持一致）
        double adjusted = raw * (1.0 - reduction) / 2.0;
        return (int) Math.max(1, Math.round(adjusted));
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
        // 返回与实际抽取值一致的浮点能耗（包含与原实现一致的 /2 单位转换）
        return raw * (1.0 - reduction) / 2.0;
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
     * 将能耗数字按单位缩写（K/M/G/T/P/E）格式化为更短的字符串
     * 例如 1500 -> "1.50K", 2000000 -> "2.00M"
     */
    public static String formatPower(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e18) return formatWithSuffix(value, 1e18, "E");
        if (abs >= 1e15) return formatWithSuffix(value, 1e15, "P");
        if (abs >= 1e12) return formatWithSuffix(value, 1e12, "T");
        if (abs >= 1e9) return formatWithSuffix(value, 1e9, "G");
        if (abs >= 1e6) return formatWithSuffix(value, 1e6, "M");
        if (abs >= 1e3) return formatWithSuffix(value, 1e3, "K");
        // 小于 1000 直接返回整数形式
        if (Math.floor(value) == value) {
            return String.format("%d", (long) value);
        }
        return String.format("%.2f", value);
    }

    private static String formatWithSuffix(double value, double unit, String suffix) {
        double v = value / unit;
        // 如果 v 是整数则不显示小数
        if (Math.abs(v - Math.round(v)) < 1e-9) {
            return String.format("%d%s", Math.round(v), suffix);
        }
        return String.format("%.2f%s", v, suffix);
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
