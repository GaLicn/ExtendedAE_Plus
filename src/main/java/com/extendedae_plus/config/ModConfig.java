package com.extendedae_plus.config;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
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
                // 配置文件加载完成后建立与当前参数一致的实体加速缓存。
                ConfigParsingUtils.reload();
                PowerUtils.initializeCaches();
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
            // 立即反映到内存实例，避免反射失败时字段与 UI 状态不一致。
            INSTANCE.extendedPatternProviderShowScalingControls = visible;

            ConfigValue<Boolean> value = (ConfigValue<Boolean>) configHolder.getValueMap()
                    .get("extendedPatternProviderShowScalingControls");
            if (value != null) {
                value.setValue(visible);
            }
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
            "JEI / EMI 中是否显示 AE2 网络库存与可合成状态",
            "可通过 JEI / EMI 左下角按钮即时切换"
    })
    public boolean jeiNetworkOverlayEnabled = true;

    @Configurable
    @Configurable.Comment(value = {
            "扩展样板供应器是否显示倍率调整按钮",
            "可通过扩展样板供应器左侧工具栏即时切换"
    })
    public boolean extendedPatternProviderShowScalingControls = true;

    @Configurable
    @Configurable.Comment(value = {
            "编码后若供应器按搜索词唯一匹配，是否直接上传到该供应器",
            "优先于装配矩阵自动上传；处理样板只可能走供应器直传",
            "客户端偏好：在客户端判定，多人游戏中每个玩家各自生效"
    })
    public boolean uniqueProviderAutoUploadEnable = true;

    @Configurable
    @Configurable.Comment(value = {
            "实体加速器能量消耗基础值"
    })
    @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
    @Configurable.Synchronized
    public int entityTickerCost = 512;

    @Configurable
    @Configurable.Comment(value = {
            "是否优先从磁盘提取FE能量（仅当Applied Flux模组存在时生效）",
            "开启后，将优先尝试从磁盘提取FE能量；反之优先消耗AE网络中的能量"
    })
    @Configurable.Synchronized
    public boolean prioritizeDiskEnergy = true;

    @Configurable
    @Configurable.Comment(value = {
            "实体加速器黑名单：匹配的方块将不会被加速。支持通配符/正则（例如：minecraft:*）",
            "格式：全名或通配符/正则字符串，例如 'minecraft:chest'、'minecraft:*'、'modid:.*_fluid'"
    })
    @Configurable.Synchronized
    public String[] entityTickerBlackList = {};

    @Configurable
    @Configurable.Comment(value = {
            "额外消耗倍率配置：为某些方块设置额外能量倍率，格式 'modid:blockid multiplier'，例如 'minecraft:chest 2x'",
            "支持通配符/正则匹配（例如 'minecraft:* 2x' 会对整个命名空间生效）。"
    })
    @Configurable.Synchronized
    public String[] entityTickerMultipliers = {};
}
