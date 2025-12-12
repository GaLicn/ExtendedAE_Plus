package com.extendedae_plus.config;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.entitySpeed.ConfigParsingUtils;
import com.extendedae_plus.util.entitySpeed.PowerUtils;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.client.IValidationHandler;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Config(id = ExtendedAEPlus.MODID)
public final class ModConfig {

    public static ModConfig INSTANCE;
    private static final Object lock = new Object();

    public static void init() {
        synchronized (lock) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(ModConfig.class, ConfigFormats.yaml()).getConfigInstance();
            }
        }
    }

    @Configurable
    @Configurable.Comment(value = {
            "扩展样板供应器总槽位容量的倍率。",
            "基础为36，每页仍显示36格，倍率会增加总页数/总容量。",
            "建议范围 1-16"
    })
    @Configurable.Synchronized
    @Configurable.Range(min = 1, max = 64)
    public int pageMultiplier = 1;

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
            "实体加速器能量消耗基础值"
    })
    @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
    @Configurable.Synchronized
    @Configurable.ValueUpdateCallback(method = "onEntityTickerCostUpdate")
    public int entityTickerCost = 512;

    @Configurable
    @Configurable.Comment(value = {
            "是否优先从磁盘提取FE能量（仅当Applied Flux模组存在时生效）",
            "开启后，将优先尝试从磁盘提取FE能量；反之优先消耗AE网络中的能量"
    })
    @Configurable.Synchronized
    @Configurable.ValueUpdateCallback(method = "onPrioritizeDiskEnergyUpdate")
    public boolean prioritizeDiskEnergy = true;

    @Configurable
    @Configurable.Comment(value = {
            "实体加速器黑名单：匹配的方块将不会被加速。支持通配符/正则（例如：minecraft:*）",
            "格式：全名或通配符/正则字符串，例如 'minecraft:chest'、'minecraft:*'、'modid:.*_fluid'"
    })
    @Configurable.Synchronized
    @Configurable.ValueUpdateCallback(method = "onEntityTickerBlackListUpdate")
    public String[] entityTickerBlackList = {};

    @Configurable
    @Configurable.Comment(value = {
            "额外消耗倍率配置：为某些方块设置额外能量倍率，格式 'modid:blockid multiplier'，例如 'minecraft:chest 2x'",
            "支持通配符/正则匹配（例如 'minecraft:* 2x' 会对整个命名空间生效）。"
    })
    @Configurable.Synchronized
    @Configurable.ValueUpdateCallback(method = "onEntityTickerMultipliersUpdate")
    public String[] entityTickerMultipliers = {};

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingPowerTask;
    private static final Object POWER_LOCK = new Object();
    private static final long DEBOUNCE_INTERVAL = 1000; // 防抖间隔，单位：毫秒

    private void onEntityTickerCostUpdate(int newValue, IValidationHandler handler) {
        synchronized (POWER_LOCK) {
            if (pendingPowerTask != null) {
                pendingPowerTask.cancel(false);
            }
            pendingPowerTask = EXECUTOR.schedule(() -> {
                synchronized (PowerUtils.class) {
                    PowerUtils.initializeCaches();
                }
            }, DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS); // 1000ms 防抖
        }
    }


    private void onEntityTickerBlackListUpdate(String[] newValue, IValidationHandler handler) {
        synchronized (ConfigParsingUtils.class) {
            ConfigParsingUtils.reload();
        }
    }

    private void onEntityTickerMultipliersUpdate(String[] newValue, IValidationHandler handler) {
        synchronized (ConfigParsingUtils.class) {
            ConfigParsingUtils.reload();
        }
    }
}