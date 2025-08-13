package com.extendedae_plus.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfigs {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue PAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue WIRELESS_MAX_RANGE;
    public static final ForgeConfigSpec.BooleanValue WIRELESS_CROSS_DIM_ENABLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("extendedae_plus");
        PAGE_MULTIPLIER = builder
                .comment(
                        "扩展样板供应器总槽位容量的倍率。",
                        "基础为36，每页仍显示36格，倍率会增加总页数/总容量。",
                        "建议范围 1-16")
                .defineInRange("pageMultiplier", 1, 1, 64);

        // 无线收发器：最大连接距离（单位：方块）。
        // 一对多从端连接主端时，将以该值作为范围限制。
        WIRELESS_MAX_RANGE = builder
                .comment(
                        "无线收发器最大连接距离（单位：方块）",
                        "从端与主端的直线距离需小于等于该值才会建立连接。")
                .defineInRange("wirelessMaxRange", 256.0D, 1.0D, 4096.0D);

        // 是否允许跨维度连接（忽略维度差异进行频道传输）。
        WIRELESS_CROSS_DIM_ENABLE = builder
                .comment(
                        "是否允许无线收发器跨维度建立连接",
                        "开启后，从端可连接到不同维度的主端（忽略距离限制）")
                .define("wirelessCrossDimEnable", true);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private ModConfigs() {}
}
