package com.extendedae_plus.wireless.endpoint;

import appeng.api.networking.IGridNode;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.wireless.IWirelessEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * IWirelessEndpoint 实现：基于 InterfaceLogicHost 与节点提供者。
 */
public class InterfaceNodeEndpointImpl implements IWirelessEndpoint {
    private final InterfaceLogicHost host;
    private final Supplier<IGridNode> nodeSupplier;

    public InterfaceNodeEndpointImpl(InterfaceLogicHost host, Supplier<IGridNode> nodeSupplier) {
        this.host = Objects.requireNonNull(host);
        this.nodeSupplier = Objects.requireNonNull(nodeSupplier);
    }

    @Override
    public ServerLevel getServerLevel() {
        var be = host.getBlockEntity();
        if (be == null) return null;
        Level lvl = be.getLevel();
        return (lvl instanceof ServerLevel) ? (ServerLevel) lvl : null;
    }

    @Override
    public BlockPos getBlockPos() {
        var be = host.getBlockEntity();
        return be != null ? be.getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public IGridNode getGridNode() {
        return nodeSupplier.get();
    }

    @Override
    public boolean isEndpointRemoved() {
        var be = host.getBlockEntity();
        return be == null || be.isRemoved();
    }
}
