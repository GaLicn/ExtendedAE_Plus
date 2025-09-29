package com.extendedae_plus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class EAEPConfig {
    // Common 配置
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.IntValue PAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue OVERRIDE_AE2WT_PICKING;
    public static final ModConfigSpec.BooleanValue INDEPENDENT_UPLOADING_BUTTON;

    // Client 配置
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_ENCODER_PATTERN_PLAYER;
    public static final ModConfigSpec.BooleanValue PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT;
    public static final ModConfigSpec.BooleanValue PRIORITIZE_DISK_ENERGY;

    // Server 配置
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.BooleanValue NEEDS_UPLOADING_CORE;
    public static final ModConfigSpec.BooleanValue PROVIDER_ROUND_ROBIN_ENABLE;
    public static final ModConfigSpec.IntValue SMART_SCALING_MAX_MULTIPLIER;
    public static final ModConfigSpec.IntValue CRAFTING_PAUSE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue WIRELESS_MAX_RANGE;
    public static final ModConfigSpec.BooleanValue WIRELESS_CROSS_DIM_ENABLE;
    public static final ModConfigSpec.IntValue ENTITY_TICKER_COST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_TICKER_BLACK_LIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_TICKER_MULTIPLIERS;

    static {
        // Common 配置
        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
        PAGE_MULTIPLIER = commonBuilder
                .comment(
                        "扩展样板供应器总槽位容量的倍率",
                        "基础为36，每页仍显示36格，倍率会增加总页数/总容量",
                        "建议范围 1-16"
                )
                .defineInRange("pageMultiplier", 1, 1, 64);
        OVERRIDE_AE2WT_PICKING = commonBuilder
                .comment("是否覆盖AE2WT使用中键从终端选取方块的逻辑",
                        "开启后选取方块的数量将不被限制在32个")
                .define("overrideAE2WTPicking", false);
        INDEPENDENT_UPLOADING_BUTTON = commonBuilder
                .comment("启用后,在样板编码终端会出现一个独立的按钮用于上传样板")
                .define("independentUploadingButton", false);
        COMMON_SPEC = commonBuilder.build();

        // Client 配置
        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        SHOW_ENCODER_PATTERN_PLAYER = clientBuilder
                .comment(
                        "是否显示样板编码玩家",
                        "开启后将在样板 HoverText 上添加样板的编码玩家")
                .define("showEncoderPatternPlayer", true);
        PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT = clientBuilder
                .comment(
                        "样板终端默认是否显示槽位",
                        "影响进入界面时SlotsRow的默认可见性，仅影响客户端显示")
                .define("patternTerminalShowSlotsDefault", true);
        CLIENT_SPEC = clientBuilder.build();

        // Server 配置
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        serverBuilder.push("ae");
        PROVIDER_ROUND_ROBIN_ENABLE = serverBuilder
                .comment(
                        "智能倍增时是否对样板供应器轮询分配",
                        "仅多个供应器有相同样板时生效，开启后请求会均分到所有可用供应器，关闭则全部分配给单一供应器",
                        "注意：所有相关供应器需开启智能倍增，否则可能失效"
                )
                .define("providerRoundRobinEnable", true);
        SMART_SCALING_MAX_MULTIPLIER = serverBuilder
                .comment(
                        "智能倍增的最大倍数（0 表示不限制）",
                        "此倍数是针对单次样板产出的放大倍数上限，用于限制一次推送中按倍增缩放的规模"
                )
                .defineInRange("smartScalingMaxMultiplier", 0, 0, 1048576);
        CRAFTING_PAUSE_THRESHOLD = serverBuilder
                .comment(
                        "值越大将减少AE构建合成计划过程中的 wait/notify 次数，提升吞吐但会降低调度响应性"
                )
                .defineInRange("craftingPauseThreshold", 100000, 100, Integer.MAX_VALUE);
        serverBuilder.pop();

        serverBuilder.push("wireless");
        WIRELESS_MAX_RANGE = serverBuilder
                .comment(
                        "无线收发器最大连接距离（单位：方块）",
                        "从端与主端的直线距离需小于等于该值才会建立连接。"
                )
                .defineInRange("wirelessMaxRange", 256.0D, 1.0D, 4096.0D);
        WIRELESS_CROSS_DIM_ENABLE = serverBuilder
                .comment(
                        "是否允许无线收发器跨维度建立连接",
                        "开启后，从端可连接到不同维度的主端（忽略距离限制）"
                )
                .define("wirelessCrossDimEnable", true);
        serverBuilder.pop();

        serverBuilder.push("entitySpeedTicker");
        ENTITY_TICKER_COST = serverBuilder
                .comment(
                        "实体加速器能量消耗基础值"
                )
                .defineInRange("entityTickerCost", 512, 0, Integer.MAX_VALUE);
        ENTITY_TICKER_BLACK_LIST = serverBuilder
                .comment(
                        "实体加速器黑名单：匹配的方块将不会被加速。支持通配符/正则（例如：minecraft:*）",
                        "格式：全名或通配符/正则字符串，例如 'minecraft:chest'、'minecraft:*'、'modid:.*_fluid'"
                )
                .defineListAllowEmpty(
                        List.of("entityTickerBlackList"),
                        List::of,
                        () -> "",
                        obj -> obj instanceof String
                );
        ENTITY_TICKER_MULTIPLIERS = serverBuilder
                .comment(
                        "额外消耗倍率配置：为某些方块设置额外能量倍率，格式 'modid:blockid multiplier'，例如 'minecraft:chest 2x'",
                        "支持通配符/正则匹配（例如 'minecraft:* 2x' 会对整个命名空间生效）。"
                )
                .defineListAllowEmpty(
                        List.of("entityTickerMultipliers"),
                        List::of,
                        () -> "",
                        obj -> obj instanceof String
                );
        PRIORITIZE_DISK_ENERGY = serverBuilder
                .comment(
                        "是否优先从磁盘提取FE能量（仅当Applied Flux模组存在时生效）",
                        "开启后，将优先尝试从磁盘提取FE能量；反之优先消耗AE网络中的能量"
                )
                .define("prioritizeDiskEnergy", true);
        serverBuilder.pop();
        NEEDS_UPLOADING_CORE = serverBuilder
                .comment("启用后, 样板只能被上传到装有上传核心的装配矩阵")
                .define("needsUploadingCore", true);
        SERVER_SPEC = serverBuilder.build();
    }

    private EAEPConfig() {}
}
