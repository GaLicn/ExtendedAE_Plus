package com.extendedae_plus.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import com.extendedae_plus.config.ModConfig;
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

        boolean crossDim = ModConfig.WIRELESS_CROSS_DIM_ENABLE.get();
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getServerLevel() == level)) {
            if (!crossDim) {
                distance = Math.sqrt(master.getBlockPos().distSqr(host.getBlockPos()));
            }
            double maxRange = ModConfig.WIRELESS_MAX_RANGE.get();
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
                        // AE2 侧是否已经存在连接（例如此前创建但 wrapper 丢失）
                        IGridConnection existing = findExistingConnection(a, b);
                        if (existing != null) {
                            connection = new ConnectionWrapper(existing);
                            return;
                        }
                        connection = new ConnectionWrapper(GridHelper.createConnection(a, b));
                        return;
                    }
                } catch (IllegalStateException ex) {
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
            var a = current.a();
            var b = current.b();
            // 先销毁连接，再唤醒两端节点，使其尽快感知到状态变化
            current.destroy();
            try {
                if (a != null && a.getGrid() != null) {
                    a.getGrid().getTickManager().wakeDevice(a);
                }
            } catch (Throwable ignored) {}
            try {
                if (b != null && b.getGrid() != null) {
                    b.getGrid().getTickManager().wakeDevice(b);
                }
            } catch (Throwable ignored) {}
            connection.setConnection(null);
        } else {
            // 兜底：如果 wrapper 已丢失，但 AE2 内仍有直连，则尝试查找并销毁
            try {
                IGridNode a = host.getGridNode();
                if (a != null) {
                    for (IGridConnection gc : a.getConnections()) {
                        // 我们创建的是非 in-world 直连
                        if (gc != null && !gc.isInWorld()) {
                            IGridNode other = gc.getOtherSide(a);
                            Object owner = other != null ? other.getOwner() : null;
                            if (owner instanceof IWirelessEndpoint) {
                                gc.destroy();
                                try { if (a.getGrid() != null) { a.getGrid().getTickManager().wakeDevice(a); } } catch (Throwable ignored) {}
                                try { if (other != null && other.getGrid() != null) { other.getGrid().getTickManager().wakeDevice(other); } } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        connection = new ConnectionWrapper(null);
    }

    /**
     * 在 AE2 中查找 a 与 b 之间是否已经有连接存在（用于复用，避免重复创建异常）。
     */
    private IGridConnection findExistingConnection(IGridNode a, IGridNode b) {
        try {
            for (IGridConnection gc : a.getConnections()) {
                var ga = gc.a();
                var gb = gc.b();
                if ((ga == a || gb == a) && (ga == b || gb == b)) {
                    return gc;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}

