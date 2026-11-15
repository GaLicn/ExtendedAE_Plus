package com.extendedae_plus.ae.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import com.extendedae_plus.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 从收发器连接器：
 * - 通过频率查找同维度主收发器；
 * - 校验距离（<= ModConfigs.WIRELESS_MAX_RANGE）；
 * - 动态创建/销毁 AE2 连接（GridConnection），实现"一主多从"。
 * <p>
 * 连接销毁一律推迟到下一个 tick 边界（与 AE2 对网格拓扑变更的处理方式一致），避免落在
 * AE2 {@code EnergyService} 的抽电调用栈内触发 provider 守卫。推迟期间不新建连接。
 */
public class WirelessSlaveLink {
    private final IWirelessEndpoint host;
    private long frequency; // 0 未设置
    @Nullable
    private UUID placerId; // 放置者UUID

    private ConnectionWrapper connection = new ConnectionWrapper(null);
    private boolean shutdown = true;
    private double distance;

    /**
     * 有连接销毁被排队时置位：在此期间既不新建连接，也不重复销毁。
     */
    private boolean teardownPending = false;

    public WirelessSlaveLink(IWirelessEndpoint host) {
        this.host = Objects.requireNonNull(host);
    }

    public void setPlacerId(@Nullable UUID placerId) {
        this.placerId = placerId;
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
        return !shutdown && !teardownPending && connection.getConnection() != null;
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

        // 上一次销毁还在排队：等它执行完再决定是否重连
        if (teardownPending) {
            return;
        }

        // placerId可以为null（公共收发器模式）
        IWirelessEndpoint master = WirelessMasterRegistry.get(level, frequency, placerId);
        shutdown = false;
        distance = 0.0D;

        boolean crossDim = ModConfig.INSTANCE.wirelessCrossDimEnable;
        if (master != null && !master.isEndpointRemoved() && (crossDim || master.getServerLevel() == level)) {
            if (!crossDim) {
                distance = Math.sqrt(master.getBlockPos().distSqr(host.getBlockPos()));
            }
            double maxRange = ModConfig.INSTANCE.wirelessMaxRange;
            if (crossDim || distance <= maxRange) {
                // 保持/建立连接
                IGridNode a = host.getGridNode(); // 从端
                IGridNode b = master.getGridNode(); // 主端
                if (a == null || b == null) {
                    shutdown = true;
                    destroyConnection();
                    return;
                }

                var current = connection.getConnection();
                if (current != null) {
                    if (DeferredGridWork.connects(current, a, b)) {
                        return; // 连接已正确
                    }
                    // 目标变了：先断开，断开完成后下一 tick 再重建
                    destroyConnection();
                    return;
                }

                IGridConnection existing = DeferredGridWork.findExistingConnection(a, b);
                if (existing != null) {
                    connection = new ConnectionWrapper(existing);
                    return;
                }

                connection = new ConnectionWrapper(GridHelper.createConnection(a, b));
                return;
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

    /**
     * 把当前连接排进下一个 tick 边界销毁。排队期间 {@link #isConnected()} 返回 false，
     * {@link #updateStatus()} 不会新建连接。
     */
    private void destroyConnection() {
        if (teardownPending) {
            return;
        }

        final ServerLevel level = host.getServerLevel();
        IGridConnection current = connection.getConnection();
        if (current != null) {
            connection.setConnection(null);
            scheduleDestroy(level, current);
            return;
        }

        // 没有记录中的连接：清理可能残留的、本应属于本端的连接
        IGridNode hostNode = host.getGridNode();
        if (hostNode == null || level == null) {
            return;
        }
        IWirelessEndpoint master = WirelessMasterRegistry.get(level, frequency, placerId);
        if (master == null) {
            return;
        }
        IGridConnection stale = DeferredGridWork.findExistingConnection(hostNode, master.getGridNode());
        if (stale != null) {
            scheduleDestroy(level, stale);
        }
    }

    private void scheduleDestroy(@Nullable ServerLevel level, IGridConnection connection) {
        teardownPending = true;
        DeferredGridWork.schedule(level, () -> {
            try {
                IGridNode a = connection.a();
                IGridNode b = connection.b();
                connection.destroy();
                DeferredGridWork.wakeDevice(a);
                DeferredGridWork.wakeDevice(b);
            } finally {
                // 无论结果如何都要放开，避免链路被永久卡在 pending 状态
                teardownPending = false;
            }
        });
    }
}
