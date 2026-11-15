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
 * 连接销毁一律推迟到下一个 tick 边界（与 AE2 对网格拓扑变更的处理方式一致），避免落在
 * AE2 {@code EnergyService} 的抽电调用栈内触发 provider 守卫。推迟期间不新建连接，
 * 与 AE2 的 {@code pendingUpdate} 语义相同。
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

        // 上一次销毁还在排队：等它执行完再决定是否重连
        if (teardownPending) {
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
            if (DeferredGridWork.connects(current, hostNode, targetNode)) {
                return; // 已经正确连接
            }
            // 目标变了：先断开，断开完成后下一 tick 再重建
            destroyConnection();
            return;
        }

        // 没有记录中的连接，但可能残留一条（例如上次在记录清除后才建好）
        IGridConnection existing = DeferredGridWork.findExistingConnection(hostNode, targetNode);
        if (existing != null) {
            connection.setConnection(existing);
            return;
        }

        connection.setConnection(GridHelper.createConnection(hostNode, targetNode));
    }

    public void onUnloadOrRemove() {
        // 先排队拆连接再清 target：残留清理分支需要 target 才能定位对端节点
        destroyConnection();
        this.target = null;
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
        IGridNode targetNode = this.target == null ? null : this.target.node();
        IGridConnection stale = DeferredGridWork.findExistingConnection(hostNode, targetNode);
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