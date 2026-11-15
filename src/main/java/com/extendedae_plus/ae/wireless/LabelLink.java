package com.extendedae_plus.ae.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 标签无线收发器的连接器。
 * - 将 BE 的 in-world 节点连接到标签网络的虚拟节点。
 * - 不负责注册/反注册标签，只负责维持物理连接。
 * <p>
 * 连接创建与销毁均推迟到下一个 tick 边界，避免在 AE2 {@code EnergyService} 的抽/注电调用栈内
 * 修改网格拓扑。待处理期间不重复建立或拆除连接。
 */
public class LabelLink {
    private final IWirelessEndpoint host;
    private final ConnectionWrapper connection = new ConnectionWrapper(null);

    @Nullable
    private LabelNetworkRegistry.LabelNetwork target;

    /**
     * 有连接销毁被排队时置位：在此期间既不新建连接，也不重复销毁。
     */
    private boolean teardownPending = false;
    private boolean connectionPending = false;

    /** 最近建立的端点节点，用于目标变化后清理失去包装器引用的旧连接。 */
    @Nullable
    private IGridNode linkedHostNode;
    @Nullable
    private IGridNode linkedTargetNode;

    public LabelLink(IWirelessEndpoint host) {
        this.host = host;
    }

    public void setTarget(@Nullable LabelNetworkRegistry.LabelNetwork target) {
        this.target = target;
        updateStatus();
    }

    public void clearTarget() {
        setTarget(null);
    }

    public boolean isConnected() {
        return !teardownPending && connection.getConnection() != null;
    }

    /**
     * 建议在 serverTick 或标签变化时调用。
     */
    public void updateStatus() {
        if (host.isEndpointRemoved()) {
            destroyConnection();
            return;
        }
        if (target == null) {
            destroyConnection();
            return;
        }

        final ServerLevel hostLevel = host.getServerLevel();
        if (hostLevel == null) {
            destroyConnection();
            return;
        }

        // 维度校验：未开启跨维且维度不匹配则断开
        ResourceKey<Level> targetDim = target.dim();
        if (targetDim != null && targetDim != hostLevel.dimension()) {
            destroyConnection();
            return;
        }

        // 前一次销毁尚未执行时，延迟判断是否重连。
        if (teardownPending || connectionPending) {
            return;
        }

        IGridNode hostNode = host.getGridNode();
        IGridNode targetNode = target.node();
        if (hostNode == null || targetNode == null) {
            destroyConnection();
            return;
        }

        var current = connection.getConnection();
        if (current != null) {
            if (DeferredGridWork.connects(current, hostNode, targetNode)
                    && DeferredGridWork.findExistingConnection(hostNode, targetNode) == current) {
                linkedHostNode = hostNode;
                linkedTargetNode = targetNode;
                return; // 已经正确连接
            }
            // 目标节点发生变化时，应先完成旧连接拆除，再创建新连接。
            destroyConnection();
            return;
        }

        if (hasDifferentLinkedNodes(hostNode, targetNode)) {
            destroyConnection();
            return;
        }

        // 未记录连接时仍可能存在残留连接，例如连接在记录清除后才建立。
        IGridConnection existing = DeferredGridWork.findExistingConnection(hostNode, targetNode);
        if (existing != null) {
            connection.setConnection(existing);
            linkedHostNode = hostNode;
            linkedTargetNode = targetNode;
            return;
        }

        clearLinkedNodes(hostNode, targetNode);
        scheduleConnection(hostLevel, target, hostNode, targetNode);
    }

    public void onUnloadOrRemove() {
        // 须在清除 target 前排队拆除连接，以便残留连接清理逻辑定位对端节点。
        destroyConnection();
        this.target = null;
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
        IGridNode targetNode = linkedTargetNode;
        if (hostNode == null || targetNode == null) {
            hostNode = host.getGridNode();
            targetNode = this.target == null ? null : this.target.node();
        }
        IGridConnection stale = DeferredGridWork.findExistingConnection(hostNode, targetNode);
        if (stale != null) {
            scheduleDestroy(level, stale);
        } else {
            clearLinkedNodes(hostNode, targetNode);
        }
    }

    private void scheduleConnection(ServerLevel level, LabelNetworkRegistry.LabelNetwork target,
                                    IGridNode hostNode, IGridNode targetNode) {
        connectionPending = true;
        DeferredGridWork.schedule(level, () -> {
            try {
                if (host.isEndpointRemoved() || this.target != target || host.getServerLevel() != level
                        || host.getGridNode() != hostNode || target.node() != targetNode
                        || (target.dim() != null && target.dim() != level.dimension())
                        || connection.getConnection() != null) {
                    return;
                }

                IGridConnection existing = DeferredGridWork.findExistingConnection(hostNode, targetNode);
                if (existing != null) {
                    connection.setConnection(existing);
                } else {
                    try {
                        connection.setConnection(GridHelper.createConnection(hostNode, targetNode));
                    } catch (IllegalStateException failure) {
                        IGridConnection raced = DeferredGridWork.findExistingConnection(hostNode, targetNode);
                        if (raced != null) {
                            connection.setConnection(raced);
                        } else {
                            throw failure;
                        }
                    }
                }

                if (connection.getConnection() != null) {
                    linkedHostNode = hostNode;
                    linkedTargetNode = targetNode;
                }
            } finally {
                connectionPending = false;
            }
        });
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

    private boolean hasDifferentLinkedNodes(IGridNode hostNode, IGridNode targetNode) {
        return linkedHostNode != null && linkedTargetNode != null
                && (linkedHostNode != hostNode || linkedTargetNode != targetNode);
    }

    private void clearLinkedNodes(@Nullable IGridNode a, @Nullable IGridNode b) {
        if ((linkedHostNode == a && linkedTargetNode == b)
                || (linkedHostNode == b && linkedTargetNode == a)) {
            linkedHostNode = null;
            linkedTargetNode = null;
        }
    }
}
