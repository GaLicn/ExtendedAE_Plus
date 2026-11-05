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
                    return; // 已经正确连接
                }
                try {
                    current.destroy();
                } catch (Throwable ignored) {}
                connection.setConnection(null);
            }
            connection.setConnection(GridHelper.createConnection(hostNode, targetNode));
        } catch (IllegalStateException ignore) {
            destroyConnection();
        }
    }

    public void onUnloadOrRemove() {
        this.target = null;
        destroyConnection();
    }

    private void destroyConnection() {
        var current = connection.getConnection();
        if (current != null) {
            var a = current.a();
            var b = current.b();
            // AE2 EnergyService forbids removeProvider while energy is being extracted.
            // Unload/shutdown can hit that race; swallow so chunk save can continue.
            try {
                current.destroy();
            } catch (Throwable ignored) {}
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
            try {
                IGridNode hostNode = this.host.getGridNode();
                IGridNode targetNode = this.target == null ? null : this.target.node();
                if (hostNode != null && targetNode != null) {
                    IGridConnection existing = this.findExistingConnection(hostNode, targetNode);
                    if (existing != null) {
                        try {
                            existing.destroy();
                        } catch (Throwable ignored) {}
                        try {
                            if (hostNode.getGrid() != null) {
                                hostNode.getGrid().getTickManager().wakeDevice(hostNode);
                            }
                        } catch (Throwable ignored) {}
                        try {
                            if (targetNode.getGrid() != null) {
                                targetNode.getGrid().getTickManager().wakeDevice(targetNode);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    @Nullable
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
