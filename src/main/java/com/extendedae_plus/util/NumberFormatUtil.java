package com.extendedae_plus.util;

/**
 * 数字格式化工具类，提供大数字和小数的格式化功能
 */
public class NumberFormatUtil {
    private NumberFormatUtil() {throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");}

    /**
     * 格式化数字，将大数字转换为k、m等格式
     * 支持小数显示，小数点后为0则不显示0
     * @param number 要格式化的数字
     * @return 格式化后的字符串
     */
    public static String formatNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            double value = number / 1000.0;
            return formatDecimal(value, "k");
        } else {
            double value = number / 1000000.0;
            return formatDecimal(value, "m");
        }
    }

    /**
     * 格式化带小数的数字，支持流体等需要显示小数的场景
     * @param value 小数值
     * @return 格式化后的字符串
     */
    public static String formatNumberWithDecimal(double value) {
        if (value < 1000) {
            if (value == (long) value) {
                return String.valueOf((long) value);
            } else {
                return String.format("%.1f", value).replaceAll("\\.0$", "");
            }
        } else if (value < 1000000) {
            return formatDecimal(value / 1000.0, "k");
        } else {
            return formatDecimal(value / 1000000.0, "m");
        }
    }

    /**
     * 格式化小数，如果小数点后为0则不显示0
     * @param value 小数值
     * @param suffix 后缀（k、m、g等）
     * @return 格式化后的字符串
     */
    private static String formatDecimal(double value, String suffix) {
        // 对于接近整数的值，使用整数显示
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return String.valueOf(Math.round(value)) + suffix;
        } else {
            // 修复重复后缀问题：先格式化数字，再添加后缀
            String formatted = String.format("%.1f", value).replaceAll("\\.0$", "");
            return formatted + suffix;
        }
    }
} 