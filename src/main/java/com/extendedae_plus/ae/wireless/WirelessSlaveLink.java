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
 * 连接创建与销毁均推迟到下一个 tick 边界，避免在 AE2 {@code EnergyService} 的抽/注电调用栈内
 * 修改网格拓扑。待处理期间不重复建立或拆除连接。
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
    private boolean connectionPending = false;

    /** 最近建立的端点节点，用于目标变化后清理失去包装器引用的旧连接。 */
    @Nullable
    private IGridNode linkedHostNode;
    @Nullable
    private IGridNode linkedMasterNode;

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

        // 前一次销毁尚未执行时，延迟判断是否重连。
        if (teardownPending || connectionPending) {
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
                    if (DeferredGridWork.connects(current, a, b)
                            && DeferredGridWork.findExistingConnection(a, b) == current) {
                        linkedHostNode = a;
                        linkedMasterNode = b;
                        return; // 连接已正确
                    }
                    // 目标节点发生变化时，应先完成旧连接拆除，再创建新连接。
                    destroyConnection();
                    return;
                }

                if (hasDifferentLinkedNodes(a, b)) {
                    destroyConnection();
                    return;
                }

                IGridConnection existing = DeferredGridWork.findExistingConnection(a, b);
                if (existing != null) {
                    connection = new ConnectionWrapper(existing);
                    linkedHostNode = a;
                    linkedMasterNode = b;
                    return;
                }

                clearLinkedNodes(a, b);
                scheduleConnection(level, master, frequency, placerId, a, b);
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
        shutdown = true;
        destroyConnection();
    }

    /**
     * 将当前连接的销毁操作排入下一个 tick 边界。排队期间 {@link #isConnected()} 返回 false，
     * {@link #updateStatus()} 不会新建连接。
     */
    private void destroyConnection() {
        if (teardownPending) {
            return;
        }

        final ServerLevel level = host.getServerLevel();
        if (level == null) {
            return;
        }
        IGridConnection current = connection.getConnection();
        if (current != null) {
            IGridNode a = current.a();
            IGridNode b = current.b();
            connection.setConnection(null);
            IGridConnection existing = DeferredGridWork.findExistingConnection(a, b);
            if (existing != null) {
                scheduleDestroy(level, existing);
            } else {
                clearLinkedNodes(a, b);
            }
            return;
        }

        IGridNode hostNode = linkedHostNode;
        IGridNode masterNode = linkedMasterNode;
        if (hostNode == null || masterNode == null) {
            hostNode = host.getGridNode();
            IWirelessEndpoint master = WirelessMasterRegistry.get(level, frequency, placerId);
            masterNode = master == null ? null : master.getGridNode();
        }
        IGridConnection stale = DeferredGridWork.findExistingConnection(hostNode, masterNode);
        if (stale != null) {
            scheduleDestroy(level, stale);
        } else {
            clearLinkedNodes(hostNode, masterNode);
        }
    }

    private void scheduleConnection(ServerLevel level, IWirelessEndpoint master, long expectedFrequency,
                                    @Nullable UUID expectedPlacerId, IGridNode hostNode, IGridNode masterNode) {
        connectionPending = true;
        DeferredGridWork.schedule(level, () -> {
            try {
                if (!isStillDesired(level, master, expectedFrequency, expectedPlacerId, hostNode, masterNode)
                        || connection.getConnection() != null) {
                    return;
                }

                IGridConnection existing = DeferredGridWork.findExistingConnection(hostNode, masterNode);
                if (existing != null) {
                    connection = new ConnectionWrapper(existing);
                } else {
                    try {
                        connection = new ConnectionWrapper(GridHelper.createConnection(hostNode, masterNode));
                    } catch (IllegalStateException failure) {
                        IGridConnection raced = DeferredGridWork.findExistingConnection(hostNode, masterNode);
                        if (raced != null) {
                            connection = new ConnectionWrapper(raced);
                        } else {
                            throw failure;
                        }
                    }
                }

                if (connection.getConnection() != null) {
                    linkedHostNode = hostNode;
                    linkedMasterNode = masterNode;
                }
            } finally {
                connectionPending = false;
            }
        });
    }

    private boolean isStillDesired(ServerLevel level, IWirelessEndpoint master, long expectedFrequency,
                                   @Nullable UUID expectedPlacerId, IGridNode hostNode, IGridNode masterNode) {
        if (shutdown || host.isEndpointRemoved() || host.getServerLevel() != level || frequency != expectedFrequency
                || !Objects.equals(placerId, expectedPlacerId) || host.getGridNode() != hostNode
                || master.isEndpointRemoved() || master.getGridNode() != masterNode
                || WirelessMasterRegistry.get(level, expectedFrequency, expectedPlacerId) != master) {
            return false;
        }

        if (!ModConfig.INSTANCE.wirelessCrossDimEnable) {
            if (master.getServerLevel() != level) {
                return false;
            }
            double currentDistance = Math.sqrt(master.getBlockPos().distSqr(host.getBlockPos()));
            if (currentDistance > ModConfig.INSTANCE.wirelessMaxRange) {
                return false;
            }
        }
        return true;
    }

    private void scheduleDestroy(ServerLevel level, IGridConnection connection) {
        teardownPending = true;
        DeferredGridWork.schedule(level, () -> {
            IGridNode a = null;
            IGridNode b = null;
            try {
                a = connection.a();
                b = connection.b();
                connection.destroy();
                clearLinkedNodes(a, b);
                DeferredGridWork.wakeDevice(a);
                DeferredGridWork.wakeDevice(b);
            } finally {
                // 无论执行结果如何均清除标志，避免链路永久处于 pending 状态。
                teardownPending = false;
            }
        });
    }

    private boolean hasDifferentLinkedNodes(IGridNode hostNode, IGridNode masterNode) {
        return linkedHostNode != null && linkedMasterNode != null
                && (linkedHostNode != hostNode || linkedMasterNode != masterNode);
    }

    private void clearLinkedNodes(@Nullable IGridNode a, @Nullable IGridNode b) {
        if ((linkedHostNode == a && linkedMasterNode == b)
                || (linkedHostNode == b && linkedMasterNode == a)) {
            linkedHostNode = null;
            linkedMasterNode = null;
        }
    }
}
