package com.extendedae_plus.wireless.endpoint;

import appeng.api.networking.IGridNode;
import com.extendedae_plus.wireless.IWirelessEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 通用 IWirelessEndpoint：通过提供方块实体与节点的 Supplier 实现。
 */
public class GenericNodeEndpointImpl implements IWirelessEndpoint {
    private final Supplier<BlockEntity> blockEntitySupplier;
    private final Supplier<IGridNode> nodeSupplier;

    public GenericNodeEndpointImpl(Supplier<BlockEntity> blockEntitySupplier, Supplier<IGridNode> nodeSupplier) {
        this.blockEntitySupplier = Objects.requireNonNull(blockEntitySupplier);
        this.nodeSupplier = Objects.requireNonNull(nodeSupplier);
    }

    @Override
    public ServerLevel getServerLevel() {
        var be = blockEntitySupplier.get();
        if (be == null) return null;
        Level lvl = be.getLevel();
        return (lvl instanceof ServerLevel) ? (ServerLevel) lvl : null;
        }

    @Override
    public BlockPos getBlockPos() {
        var be = blockEntitySupplier.get();
        return be != null ? be.getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public IGridNode getGridNode() {
        return nodeSupplier.get();
    }

    @Override
    public boolean isEndpointRemoved() {
        var be = blockEntitySupplier.get();
        return be == null || be.isRemoved();
    }
}
