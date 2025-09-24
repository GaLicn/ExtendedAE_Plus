package com.extendedae_plus.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * 从收发器连接器：
 * - 通过频率查找同维度主收发器；
 * - 校验距离（<= ModConfigs.WIRELESS_MAX_RANGE）；
 * - 动态创建/销毁 AE2 连接（GridConnection），实现“一主多从”。
 */
public class WirelessSlaveLink {
    private final IWirelessEndpoint host;
    private long frequency; // 0 未设置

    private ConnectionWrapper connection = new ConnectionWrapper(null);
    private boolean shutdown = true;
    private double distance;

    public WirelessSlaveLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }

    public void setFrequency(long frequency) {
        if (this.frequency != frequency) {
            this.frequency = frequency;
            // 频率变更，立即尝试重连/断开
            updateStatus();
        }
    }

    public long getFrequency() {
        return frequency;
    }

    public boolean isConnected() {
        return !shutdown && connection.getConnection() != null;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * 建议在 BE 的 serverTick 或者频率/加载状态变化时调用。
     */
    public void updateStatus() {
        if (host.isEndpointRemoved()) {
            ExtendedAELogger.LOGGER.debug("[无线] 端点已移除或无效，销毁连接");
            destroyConnection();
            return;
        }
        final ServerLevel level = host.getServerLevel();
        if (level == null || frequency == 0L) {
            ExtendedAELogger.LOGGER.debug("[无线] 环境不满足：level={}, freq={}", level, frequency);
            destroyConnection();
            return;
        }

        IWirelessEndpoint master = WirelessMasterRegistry.get(level, frequency);
        ExtendedAELogger.LOGGER.debug("[无线] 查找主站: level={}, freq={} -> {}", level.dimension(), frequency, master);
        shutdown = false;
        distance = 0.0D;

        boolean crossDim = ModConfigs.WIRELESS_CROSS_DIM_ENABLE.get();
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getServerLevel() == level)) {
            if (!crossDim) {
                distance = Math.sqrt(master.getBlockPos().distSqr(host.getBlockPos()));
                ExtendedAELogger.LOGGER.debug("[无线] 同维度距离={}, 最大距离={}", distance, ModConfigs.WIRELESS_MAX_RANGE.get());
            }
            double maxRange = ModConfigs.WIRELESS_MAX_RANGE.get();
            if (crossDim || distance <= maxRange) {
                // 保持/建立连接
                try {
                    var current = connection.getConnection();
                    IGridNode a = host.getGridNode(); // 从端
                    IGridNode b = master.getGridNode(); // 主端
                    if (a == null) { ExtendedAELogger.LOGGER.debug("[无线] 从端节点为 null，无法连接"); }
                    if (b == null) { ExtendedAELogger.LOGGER.debug("[无线] 主端节点为 null，无法连接"); }
                    if (a == null || b == null) {
                        shutdown = true;
                    } else {
                        if (current != null) {
                            // 如果已连且目标相同则维持
                            var ca = current.a();
                            var cb = current.b();
                            if ((ca == a || cb == a) && (ca == b || cb == b)) {
                                ExtendedAELogger.LOGGER.debug("[无线] 连接已存在且目标一致，保持");
                                return; // 连接已正确
                            }
                            // 否则先断开，再重建
                            ExtendedAELogger.LOGGER.debug("[无线] 连接目标变化，先销毁再重建");
                            current.destroy();
                            connection = new ConnectionWrapper(null);
                        }
                        ExtendedAELogger.LOGGER.debug("[无线] 创建连接: a={}, b={}", a, b);
                        connection = new ConnectionWrapper(GridHelper.createConnection(a, b));
                        ExtendedAELogger.LOGGER.debug("[无线] 连接创建完成: {}", connection.getConnection());
                        return;
                    }
                } catch (IllegalStateException ex) {
                    // 连接非法（如重复连接等）——落入重建/关闭逻辑
                    ExtendedAELogger.LOGGER.debug("[无线] 连接创建异常: {}", ex.toString());
                }
            } else {
                ExtendedAELogger.LOGGER.debug("[无线] 超出范围：{} > {}，关闭连接", distance, maxRange);
                shutdown = true; // 超出范围
            }
        } else {
            ExtendedAELogger.LOGGER.debug("[无线] 无可用主站或跨维度不允许，关闭连接");
            shutdown = true; // 无主或主端不可用
        }

        // 需要关闭连接
        destroyConnection();
    }

    public void onUnloadOrRemove() {
        destroyConnection();
    }

    private void destroyConnection() {
        var current = connection.getConnection();
        if (current != null) {
            current.destroy();
            connection.setConnection(null);
        }
        connection = new ConnectionWrapper(null);
    }
}
