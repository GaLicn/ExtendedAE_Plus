package com.extendedae_plus.util;

import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.config.ModConfig;

/**
 * 用于计算实体加速器的能耗与加速倍率的工具类
 */
public final class PowerUtils {
    private PowerUtils() {}
    // ---- 重构后的 API ----
    /**
     * 将 card multipliers（按插槽序）计算乘积并应用 cap 规则（见 capForHighestMultiplier）
     * @param multipliers iterable of per-card multipliers
     * @param maxCards 最多计入的卡数
     * @return 被 cap 约束后的乘积
     */
    public static long computeProductWithCap(Iterable<Integer> multipliers, int maxCards) {
        long product = 1L;
        int considered = 0;
        int highest = 1;
        for (Integer m : multipliers) {
            if (m == null) continue;
            if (considered >= maxCards) break;
            int mult = m.intValue();
            if (mult <= 0) mult = 1;
            product *= mult;
            highest = Math.max(highest, mult);
            considered++;
        }
        long cap = capForHighestMultiplier(highest);
        return Math.min(product, cap);
    }

    /**
     * 根据最高单卡 multiplier 返回 cap 值
     */
    public static long capForHighestMultiplier(int highestMultiplier) {
        if (highestMultiplier >= 16) return 1024L;
        if (highestMultiplier >= 8) return 256L;
        if (highestMultiplier >= 4) return 64L;
        if (highestMultiplier >= 2) return 8L;
        return 1L;
    }

    /**
     * 从菜单对象读取前 maxCards 个加速卡的 multiplier 并计算 product with cap
     */
    public static long computeProductWithCapFromMenu(appeng.menu.implementations.UpgradeableMenu<?> menu, int maxCards) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        int considered = 0;
        for (var stack : menu.getUpgrades()) {
            if (considered >= maxCards) break;
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof EntitySpeedCardItem) {
                int multVal = EntitySpeedCardItem.readMultiplier(stack);
                int count = Math.min(stack.getCount(), maxCards - considered);
                for (int i = 0; i < count; i++) {
                    list.add(multVal);
                    considered++;
                    if (considered >= maxCards) break;
                }
            }
        }
        return computeProductWithCap(list, maxCards);
    }

    /**
     * 从一组 ItemStack（升级槽）直接计算 product with cap（最多 maxCards）
     */
    public static long computeProductWithCapFromStacks(Iterable<net.minecraft.world.item.ItemStack> stacks, int maxCards) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        int considered = 0;
        for (var stack : stacks) {
            if (considered >= maxCards) break;
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof EntitySpeedCardItem) {
                int multVal = EntitySpeedCardItem.readMultiplier(stack);
                int count = Math.min(stack.getCount(), maxCards - considered);
                for (int i = 0; i < count; i++) {
                    list.add(multVal);
                    considered++;
                    if (considered >= maxCards) break;
                }
            }
        }
        return computeProductWithCap(list, maxCards);
    }

    /**
     * 计算最终消耗：把 product 转换为等效卡数（log2）并调用 getFinalPower
     */
    public static double computeFinalPowerForProduct(long product, int energyCardCount) {
        if (product <= 1L) return 0.0;
        double base = ModConfig.INSTANCE.entityTickerCost;

        // 计算以2为底的对数（用于分档与公式）
        double log2 = Math.log(product) / Math.log(2.0);

        // 分档：product==2 为一档；4..256 为中档；512..1024 为高档
        double raw;
        if (product == 2L) {
            // 轻量档：线性小幅增长
            raw = base * 4;
        } else if (product <= 256L) {
            // 中档：增长放缓（使用 1.5 * log2）
            raw = base * Math.pow(2.0, 1.5 * log2) * 2;
        } else {
            // 高档：增长较快（使用 2.5 * log2）
            raw = base * Math.pow(2.0, 2.5 * log2);
        }

        double reduction = getReductionPercent(energyCardCount);
        return raw * (1.0 - reduction) / 8.0;
    }

    /* ----------------- legacy helpers (restored) ----------------- */
    public static double getGrowthFactor(int speedCardCount) {
        if (speedCardCount <= 0) return 1.0;
        if (speedCardCount == 1) return 2.0;
        if (speedCardCount <= 6) return Math.pow(2.0, 2.0 * speedCardCount);
        return Math.pow(2.0, 3.0 * speedCardCount);
    }

    public static double getRawPower(int speedCardCount) {
        double base = ModConfig.INSTANCE.entityTickerCost;
        return base * getGrowthFactor(speedCardCount);
    }

    public static double getReductionPercent(int energyCardCount) {
        if (energyCardCount <= 0) return 0.0;
        if (energyCardCount == 1) return 0.1;
        if (energyCardCount >= 8) return 0.5;
        return 0.5 * (1.0 - Math.pow(0.7, energyCardCount));
    }

    public static double getFinalPower(int speedCardCount, int energyCardCount) {
        double raw = getRawPower(speedCardCount);
        double reduction = getReductionPercent(energyCardCount);
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
