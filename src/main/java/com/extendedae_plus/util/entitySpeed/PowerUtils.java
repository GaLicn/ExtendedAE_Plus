package com.extendedae_plus.util.entitySpeed;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.config.ModConfig;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于计算实体加速器的能耗与加速倍率的工具类
 */
public final class PowerUtils {
    private PowerUtils() {}

    /**
     * 计算加速卡的乘积并应用上限。
     * @param upgrades 升级槽位
     * @param maxCards 最大计入的卡数
     * @return 被上限约束后的乘积
     */
    public static long computeProductWithCap(IUpgradeInventory upgrades, int maxCards) {
        List<Integer> multipliers = new ArrayList<>();
        int considered = 0;
        for (ItemStack stack : upgrades) {
            if (considered >= maxCards) break;
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof EntitySpeedCardItem) {
                int multVal = EntitySpeedCardItem.readMultiplier(stack);
                int count = Math.min(stack.getCount(), maxCards - considered);
                for (int i = 0; i < count; i++) {
                    multipliers.add(multVal);
                    considered++;
                }
            }
        }
        long product = 1L;
        int highest = 1;
        for (Integer m : multipliers) {
            if (m == null || m <= 0) continue;
            product *= m;
            highest = Math.max(highest, m);
        }
        return Math.min(product, capForHighestMultiplier(highest));
    }

    /**
     * 根据最高单卡倍率返回上限值。
     */
    public static long capForHighestMultiplier(int highestMultiplier) {
        if (highestMultiplier >= 16) return 1024L;
        if (highestMultiplier >= 8) return 256L;
        if (highestMultiplier >= 4) return 64L;
        if (highestMultiplier >= 2) return 8L;
        return 1L;
    }

    /**
     * 计算最终能耗。
     * @param product 加速卡乘积
     * @param energyCardCount 能量卡数量
     * @return 最终能耗值
     */
    public static double computeFinalPowerForProduct(long product, int energyCardCount) {
        if (product <= 1L) return 0.0;
        double base = ModConfig.INSTANCE.entityTickerCost;
        double log2 = Math.log(product) / Math.log(2.0);
        double raw;
        if (product == 2L) {
            raw = base * 4;
        } else if (product <= 256L) {
            raw = base * Math.pow(2.0, 1.5 * log2) * 2;
        } else {
            raw = base * Math.pow(2.0, 2.5 * log2);
        }
        return raw * getRemainingRatio(energyCardCount) / 8.0;
    }

    /**
     * 计算能量卡减免后的剩余功耗比率。
     */
    public static double getRemainingRatio(int energyCardCount) {
        if (energyCardCount <= 0) return 1.0;
        if (energyCardCount == 1) return 0.9;
        if (energyCardCount >= 8) return 0.5;
        return 1.0 - 0.5 * (1.0 - Math.pow(0.7, energyCardCount));
    }

    /**
     * 将剩余功耗比率格式化为百分比字符串。
     */
    public static String formatPercentage(double ratio) {
        double pct = ratio * 100.0;
        return Math.abs(pct - Math.round(pct)) < 1e-9 ?
                String.format("%d%%", Math.round(pct)) :
                String.format("%.2f%%", pct);
    }
}