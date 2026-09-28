package com.extendedae_plus.config;

import com.extendedae_plus.ExtendedAEPlus;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.io.ConfigIO;
import dev.toma.configuration.config.value.ConfigValue;


@Config(id = ExtendedAEPlus.MODID)
public final class ModConfig {

    public static ModConfig INSTANCE;
    private static ConfigHolder<ModConfig> configHolder;
    private static final Object lock = new Object();

    public static void init() {
        synchronized (lock) {
            if (INSTANCE == null) {
                configHolder = Configuration.registerConfig(ModConfig.class, ConfigFormats.yaml());
                INSTANCE = configHolder.getConfigInstance();
            }
        }
    }

    /** Updates and immediately saves a client-only UI preference. */
    @SuppressWarnings("unchecked")
    public static void setExtendedPatternProviderShowScalingControls(boolean visible) {
        synchronized (lock) {
            if (INSTANCE == null || configHolder == null) {
                return;
            }
            ConfigValue<Boolean> value = (ConfigValue<Boolean>) configHolder.getValueMap()
                    .get("extendedPatternProviderShowScalingControls");
            value.set(visible);
            ConfigIO.saveClientValues(configHolder);
        }
    }

    @Configurable
    @Configurable.Comment(value = {
            "设置AE构建合成计划过程中的 wait/notify 次数，提升吞吐但会降低调度响应性"
    })
    @Configurable.Synchronized
    @Configurable.Range(min = 100, max = Integer.MAX_VALUE)
    public int craftingPauseThreshold = 100000;

    @Configurable
    @Configurable.Comment(value = {
            "无线收发器最大连接距离（单位：方块）",
            "从端与主端的直线距离需小于等于该值才会建立连接"
    })
    @Configurable.Synchronized
    @Configurable.DecimalRange(min = 1, max = 4096)
    public double wirelessMaxRange = 256.0;

    @Configurable
    @Configurable.Comment(value = {
            "是否允许无线收发器跨维度建立连接",
            "开启后，从端可连接到不同维度的主端（忽略距离限制）"
    })
    @Configurable.Synchronized
    public boolean wirelessCrossDimEnable = true;

    @Configurable
    @Configurable.Comment(value = {
            "无线收发器待机能耗",
            "无线收发器的基础待机能耗（AE/t），同时作用于普通与标签无线收发器"
    })
    @Configurable.Synchronized
    @Configurable.DecimalRange(min = 0, max = Double.MAX_VALUE)
    public double wirelessTransceiverIdlePower = 100.0;

    @Configurable
    @Configurable.Comment(value = {
            "智能倍增时是否对样板供应器轮询分配",
            "仅多个供应器有相同样板时生效，开启后请求会均分到所有可用供应器，关闭则全部分配给单一供应器",
            "注意：所有相关供应器需开启智能倍增，否则可能失效"
    })
    @Configurable.Synchronized
    public boolean providerRoundRobinEnable = true;

    @Configurable
    @Configurable.Comment(value = {
            "全局智能倍增的最大倍率限制（0 表示不限制）",
            "此限制针对单次样板产出的倍增上限，用于控制一次推送的最大缩放规模",
            "优先级低于样板自身的供应器限制"
    })
    @Configurable.Synchronized
    @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
    public int smartScalingMaxMultiplier = 0;

    @Configurable
    @Configurable.Comment(value = {
            "是否显示样板编码玩家",
            "开启后将在样板 HoverText 上添加样板的编码玩家"
    })
    public boolean showEncoderPatternPlayer = true;

    @Configurable
    @Configurable.Comment(value = {
            "样板终端默认是否显示槽位",
            "影响进入界面时SlotsRow的默认可见性，仅影响客户端显示"
    })
    public boolean patternTerminalShowSlotsDefault = true;

    @Configurable
    @Configurable.Comment(value = {
            "样板终端默认是否合并空槽位",
            "开启后只会显示到最后一个有物品的槽位之后的一个空槽位（直到占满供应器所有槽位）"
    })
    public boolean patternTerminalMergeEmptySlotsDefault = true;

    @Configurable
    @Configurable.Comment(value = {
            "JEI 中是否显示 AE2 网络库存与可合成状态",
            "可通过 JEI 左下角按钮即时切换"
    })
    public boolean jeiNetworkOverlayEnabled = true;

    @Configurable
    @Configurable.Comment(value = {
            "扩展样板供应器是否显示倍率调整按钮",
            "可通过扩展样板供应器左侧工具栏即时切换"
    })
    public boolean extendedPatternProviderShowScalingControls = true;

    }
