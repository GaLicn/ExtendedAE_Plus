package com.extendedae_plus.ae.wireless;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.ConnectionWrapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 标签无线收发器的连接器。
 * - 将 BE 的 in-world 节点连接到标签网络的虚拟节点。
 * - 只维护物理连接，不负责注册/反注册标签。
 */
public class LabelLink {
    private final IWirelessEndpoint host;
    private final ConnectionWrapper connection = new ConnectionWrapper(null);

    @Nullable
    private LabelNetworkRegistry.LabelNetwork target;

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
        return connection.getConnection() != null;
    }

    /**
     * 在 serverTick 或标签变化时调用。
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

        IGridNode hostNode = host.getGridNode();
        IGridNode targetNode = target.node();
        if (hostNode == null || targetNode == null) {
            destroyConnection();
            return;
        }

        try {
            var current = connection.getConnection();
            if (current != null) {
                var a = current.a();
                var b = current.b();
                if ((a == hostNode || b == hostNode) && (a == targetNode || b == targetNode)) {
                    return; // 已连接
                }
                current.destroy();
                connection.setConnection(null);
            }
            connection.setConnection(GridHelper.createConnection(hostNode, targetNode));
        } catch (IllegalStateException ignore) {
            destroyConnection();
        }
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
    }
}
