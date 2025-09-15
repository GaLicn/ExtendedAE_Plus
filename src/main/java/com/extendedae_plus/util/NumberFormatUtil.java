package com.extendedae_plus.util;

import java.text.DecimalFormat;

/**
 * 数字格式化工具类，提供大数字和小数的格式化功能
 */
public class NumberFormatUtil {
    private NumberFormatUtil() {throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");}

    /**
     * 格式化数字，将大数字转换为 k、M、G 等格式（物品用整数/一位小数显示规则）
     * @param number 要格式化的数字
     * @return 格式化后的字符串
     */
    public static String formatNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        }

        String[] preFixes = new String[]{"k", "M", "G", "T", "P", "E", "Z", "Y"};
        double value = number;
        String level = "";

        for (int offset = 0; value >= 1000.0 && offset < preFixes.length; ++offset) {
            value /= 1000.0;
            level = preFixes[offset];
        }

        return formatDecimal(value, level);
    }

    /**
     * 格式化带小数的数字，支持流体等需要显示小数的场景（保留至多两位小数）
     * 流体显示规则不同于物品，统一使用两位小数的 DecimalFormat
     * @param value 小数值
     * @return 格式化后的字符串
     */
    public static String formatNumberWithDecimal(double value) {
        if (value < 1000) {
            DecimalFormat smallDf = new DecimalFormat("#.##");
            // 小于1000时，若是整数则显示整数，否则显示最多两位小数
            if (value == (long) value) {
                return String.valueOf((long) value);
            } else {
                return smallDf.format(value);
            }
        }

        String[] preFixes = new String[]{"k", "M", "G", "T", "P", "E", "Z", "Y"};
        String level = "";
        for (int offset = 0; value >= 1000.0 && offset < preFixes.length; ++offset) {
            value /= 1000.0;
            level = preFixes[offset];
        }

        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(value) + level;
    }

    /**
     * 格式化小数（物品显示规则）：如果小数点后接近 0 则显示为整数，否则显示一位小数
     * @param value 小数值
     * @param suffix 后缀（k、M、G 等）
     * @return 格式化后的字符串
     */
    private static String formatDecimal(double value, String suffix) {
        // 对于接近整数的值，使用整数显示
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return String.valueOf(Math.round(value)) + suffix;
        } else {
            // 使用一位小数显示，去掉末尾 .0
            String formatted = String.format("%.1f", value).replaceAll("\\.0$", "");
            return formatted + suffix;
        }
    }
}