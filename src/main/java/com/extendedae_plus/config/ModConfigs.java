package com.extendedae_plus.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfigs {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue PAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue WIRELESS_MAX_RANGE;
    public static final ForgeConfigSpec.BooleanValue WIRELESS_CROSS_DIM_ENABLE;
    public static final ForgeConfigSpec.BooleanValue SHOW_ENCOD_PATTERN_PLAYER;
    public static final ForgeConfigSpec.BooleanValue PROVIDER_ROUND_ROBIN_ENABLE;

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

        SHOW_ENCOD_PATTERN_PLAYER = builder
                .comment(
                        "是否显示样板编码玩家",
                        "开启后将在样板 HoverText 上添加样板的编码玩家"
                )
                .define("showEncoderPatternPlayer", true);

        // 智能倍增后，是否在样板供应器间轮询分配请求量（开启：按 provider 均分；关闭：不拆分）
        PROVIDER_ROUND_ROBIN_ENABLE = builder
                .comment(
                        "智能倍增时是否对样板供应器轮询分配",
                        "仅多个供应器有相同样板时生效，开启后请求会均分到所有可用供应器，关闭则全部分配给单一供应器",
                        "注意：所有相关供应器需开启智能倍增，否则可能失效",
                        "默认: true")
                .define("providerRoundRobinEnable", true);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private ModConfigs() {}
}
