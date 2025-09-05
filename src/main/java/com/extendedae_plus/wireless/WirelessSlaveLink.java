package com.extendedae_plus.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import com.extendedae_plus.config.ModConfigs;
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
            destroyConnection();
            return;
        }
        final ServerLevel level = host.getServerLevel();
        if (level == null || frequency == 0L) {
            destroyConnection();
            return;
        }

        IWirelessEndpoint master = WirelessMasterRegistry.get(level, frequency);
        shutdown = false;
        distance = 0.0D;

        boolean crossDim = ModConfigs.WIRELESS_CROSS_DIM_ENABLE.get();
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getServerLevel() == level)) {
            if (!crossDim) {
                distance = Math.sqrt(master.getBlockPos().distSqr(host.getBlockPos()));
            }
            double maxRange = ModConfigs.WIRELESS_MAX_RANGE.get();
            if (crossDim || distance <= maxRange) {
                // 保持/建立连接
                try {
                    var current = connection.getConnection();
                    IGridNode a = host.getGridNode(); // 从端
                    IGridNode b = master.getGridNode(); // 主端
                    if (a == null || b == null) {
                        shutdown = true;
                    } else {
                        if (current != null) {
                            // 如果已连且目标相同则维持
                            var ca = current.a();
                            var cb = current.b();
                            if ((ca == a || cb == a) && (ca == b || cb == b)) {
                                return; // 连接已正确
                            }
                            // 否则先断开，再重建
                            current.destroy();
                            connection = new ConnectionWrapper(null);
                        }
                        connection = new ConnectionWrapper(GridHelper.createConnection(a, b));
                        return;
                    }
                } catch (IllegalStateException ignore) {
                    // 连接非法（如重复连接等）——落入重建/关闭逻辑
                }
            } else {
                shutdown = true; // 超出范围
            }
        } else {
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
