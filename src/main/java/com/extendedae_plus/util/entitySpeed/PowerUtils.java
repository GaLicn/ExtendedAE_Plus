package com.extendedae_plus.util.entitySpeed;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于计算实体加速器的能耗与加速倍率的工具类
 */
public final class PowerUtils {
    private static final int[] VALID_MULTIPLIERS = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
    private static final int[] VALID_ENERGY_CARD_COUNTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static volatile Map<Integer, Map<Integer, Double>> powerCache = new HashMap<>();
    private static volatile Map<Integer, Double> ratioCache = new HashMap<>();

    // 静态初始化块，预计算缓存
    static {
        initializeCaches();
    }

    private PowerUtils() {}

    /**
     * 初始化所有可能的缓存条目
     */
    public static void initializeCaches() {
        synchronized (PowerUtils.class) {
            powerCache.clear();
            ratioCache.clear();
            for (int energyCardCount : VALID_ENERGY_CARD_COUNTS) {
                double ratio =
                        energyCardCount == 0 ? 1.0 :
                        energyCardCount == 1 ? 0.9 :
                        energyCardCount == 8 ? 0.5 :
                        1.0 - 0.5 * (1.0 - Math.pow(0.7, energyCardCount));
                ratioCache.put(energyCardCount, ratio);
            }
            for (int product : VALID_MULTIPLIERS) {
                Map<Integer, Double> energyCardMap = new HashMap<>();
                for (int energyCardCount : VALID_ENERGY_CARD_COUNTS) {
                    double power = computePowerForProduct(product, energyCardCount);
                    energyCardMap.put(energyCardCount, power);
                }
                powerCache.put(product, energyCardMap);
            }
        }
    }

    /**
     * 计算指定倍率和能源卡数量的能耗
     */
    private static double computePowerForProduct(int product, int energyCardCount) {
        double base = ModConfig.INSTANCE.entityTickerCost;
        int log2 = Integer.numberOfTrailingZeros(product);
        double raw;
        if (product == 2) {
            raw = base * 4;
        } else if (product <= 256) {
            raw = base * (1L << (int)(1.5 * log2)) * 2;
        } else {
            raw = base * (1L << (int)(2.5 * log2));
        }
        double ratio =
                energyCardCount <= 0 ? 1.0 :
                energyCardCount == 1 ? 0.9 :
                energyCardCount >= 8 ? 0.5 :
                1.0 - 0.5 * (1.0 - Math.pow(0.7, energyCardCount));
        return raw * ratio / 8.0;
    }

    /**
     * 计算加速卡的乘积并应用上限。
     *
     * @param upgrades 升级槽位
     * @param maxCards 最大计入的卡数
     * @return 被上限约束后的乘积
     */
    public static int computeProductWithCap(IUpgradeInventory upgrades, int maxCards) {
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
        int product = 1;
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
    public static int capForHighestMultiplier(int highestMultiplier) {
        if (highestMultiplier >= 16) return 1024;
        if (highestMultiplier >= 8) return 256;
        if (highestMultiplier >= 4) return 64;
        if (highestMultiplier >= 2) return 8;
        return 1;
    }

    /**
     * 直接从缓存获取能耗值
     *
     * @param product         加速卡乘积
     * @param energyCardCount 能量卡数量
     * @return 缓存的能耗值
     */
    public static double getCachedPower(int product, int energyCardCount) {
        if (product <= 1) return 0.0;
        Map<Integer, Double> energyCardMap = powerCache.get(product);
        if (energyCardMap == null) return 0.0;
        Double cachedPower = energyCardMap.get(energyCardCount);
        if (cachedPower == null) return 0.0;
        return cachedPower;
    }

    /**
     * 直接从缓存获取比率（无配置检查）。
     *
     * @param energyCardCount 能量卡数量
     * @return 缓存的比率值
     */
    public static double getCachedRatio(int energyCardCount) {
        Double cachedRatio = ratioCache.get(energyCardCount);
        if (cachedRatio == null) return 1.0;
        return cachedRatio;
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