package com.extendedae_plus.ae.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import com.extendedae_plus.config.ModConfigs;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 从收发器连接器：
 * - 通过频率查找同维度主收发器；
 * - 校验距离（<= ModConfigs.WIRELESS_MAX_RANGE）；
 * - 动态创建/销毁 AE2 连接（GridConnection），实现"一主多从"。
 */
public class WirelessSlaveLink {
    private final IWirelessEndpoint host;
    private long frequency; // 0 未设置
    @Nullable
    private UUID placerId; // 放置者UUID

    private ConnectionWrapper connection = new ConnectionWrapper(null);
    private boolean shutdown = true;
    private double distance;

    public WirelessSlaveLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }
    
    public void setPlacerId(@Nullable UUID placerId) {
        this.placerId = placerId;
    }

    public long getFrequency() {
        return this.frequency;
    }

    public void setFrequency(long frequency) {
        if (this.frequency != frequency) {
            this.frequency = frequency;
            // 频率变更，立即尝试重连/断开
            this.updateStatus();
        }
    }

    public boolean isConnected() {
        return !this.shutdown && this.connection.getConnection() != null;
    }

    public double getDistance() {
        return this.distance;
    }

    /**
     * 建议在 BE 的 serverTick 或者频率/加载状态变化时调用。
     */
    public void updateStatus() {
        if (this.host.isEndpointRemoved()) {
            this.destroyConnection();
            return;
        }
        final ServerLevel level = this.host.getServerLevel();
        if (level == null || this.frequency == 0L) {
            this.destroyConnection();
            return;
        }

        // placerId可以为null（公共收发器模式）
        IWirelessEndpoint master = WirelessMasterRegistry.get(level, this.frequency, this.placerId);
        this.shutdown = false;
        this.distance = 0.0D;

        boolean crossDim = ModConfigs.WIRELESS_CROSS_DIM_ENABLE.get();
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getServerLevel() == level)) {
            if (!crossDim) {
                this.distance = Math.sqrt(master.getBlockPos().distSqr(this.host.getBlockPos()));
            }
            double maxRange = ModConfigs.WIRELESS_MAX_RANGE.get();
            if (crossDim || this.distance <= maxRange) {
                // 保持/建立连接
                try {
                    var current = this.connection.getConnection();
                    IGridNode a = this.host.getGridNode(); // 从端
                    IGridNode b = master.getGridNode(); // 主端
                    if (a == null || b == null) {
                        this.shutdown = true;
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
                            this.connection = new ConnectionWrapper(null);
                        }
                        // AE2 侧是否已经存在连接（例如此前创建但 wrapper 丢失）
                        IGridConnection existing = this.findExistingConnection(a, b);
                        if (existing != null) {
                            this.connection = new ConnectionWrapper(existing);
                            return;
                        }
                        this.connection = new ConnectionWrapper(GridHelper.createConnection(a, b));
                        return;
                    }
                } catch (IllegalStateException ex) {
                    // 连接非法（如重复连接等）——落入重建/关闭逻辑
                }
            } else {
                this.shutdown = true; // 超出范围
            }
        } else {
            this.shutdown = true; // 无主或主端不可用
        }

        // 需要关闭连接
        this.destroyConnection();
    }

    public void onUnloadOrRemove() {
        this.destroyConnection();
    }

    private void destroyConnection() {
        var current = this.connection.getConnection();
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
            this.connection.setConnection(null);
        } else {
            // 兜底：如果 wrapper 已丢失，但 AE2 内仍有直连，则尝试查找并销毁
            try {
                IGridNode a = this.host.getGridNode();
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
        this.connection = new ConnectionWrapper(null);
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

