package com.extendedae_plus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfigs {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.IntValue PAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WIRELESS_MAX_RANGE;
    public static final ModConfigSpec.BooleanValue WIRELESS_CROSS_DIM_ENABLE;
    public static final ModConfigSpec.BooleanValue SHOW_ENCOD_PATTERN_PLAYER;
    public static final ModConfigSpec.BooleanValue PROVIDER_ROUND_ROBIN_ENABLE;
    public static final ModConfigSpec.BooleanValue PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT;
    public static final ModConfigSpec.IntValue SMART_SCALING_MAX_MULTIPLIER;
    public static final ModConfigSpec.IntValue CRAFTING_PAUSE_THRESHOLD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // General settings
        builder.push("general");
        PAGE_MULTIPLIER = builder
                .comment(
                        "扩展样板供应器总槽位容量的倍率",
                        "基础为36，每页仍显示36格，倍率会增加总页数/总容量",
                        "建议范围 1-16")
                .defineInRange("pageMultiplier", 1, 1, 64);

        // 是否显示样板编码玩家（通用）
        SHOW_ENCOD_PATTERN_PLAYER = builder
                .comment(
                        "是否显示样板编码玩家",
                        "开启后将在样板 HoverText 上添加样板的编码玩家"
                )
                .define("showEncoderPatternPlayer", true);

        // 模式访问终端（ExtendedAE 图样终端）默认是否显示槽位渲染（SlotsRow）。
        // true: 默认显示（可通过界面按钮临时隐藏）；false: 默认隐藏（可通过按钮显示）
        PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT = builder
                .comment(
                        "样板终端默认是否显示槽位",
                        "影响进入界面时SlotsRow的默认可见性，仅影响客户端显示"
                )
                .define("patternTerminalShowSlotsDefault", true);

        CRAFTING_PAUSE_THRESHOLD = builder
                .comment(
                        "值越大将减少AE构建合成计划过程中的 wait/notify 次数，提升吞吐但会降低调度响应性"
                )
                .defineInRange("craftingPauseThreshold", 100000, 100, Integer.MAX_VALUE);

        // end general
        builder.pop();

        // Smart-scaling group
        builder.push("smartScaling");
        // 智能倍增：是否在样板供应器间轮询分配请求量（开启：按 provider 均分；关闭：不拆分）
        PROVIDER_ROUND_ROBIN_ENABLE = builder
                .comment(
                        "智能倍增时是否对样板供应器轮询分配",
                        "仅多个供应器有相同样板时生效，开启后请求会均分到所有可用供应器，关闭则全部分配给单一供应器",
                        "注意：所有相关供应器需开启智能倍增，否则可能失效",
                        "默认: true")
                .define("providerRoundRobinEnable", true);

        // 智能倍增的最大倍数（以单次样板产出为单位）。
        // 0 表示不限制；>0 表示最大倍增倍数上限，例如 64 表示最多放大到 64 倍。
        SMART_SCALING_MAX_MULTIPLIER = builder
                .comment(
                        "智能倍增的最大倍数（0 表示不限制）",
                        "此倍数是针对单次样板产出的放大倍数上限，用于限制一次推送中按倍增缩放的规模")
                .defineInRange("smartScalingMaxMultiplier", 0, 0, 1048576);

        builder.pop(); // pop smart

        // Wireless settings
        builder.push("wireless");
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

        builder.pop(); // pop wireless

//        builder.pop(); // pop extendedae_plus
        COMMON_SPEC = builder.build();
    }

    private ModConfigs() {
    }
}
