package com.extendedae_plus.content.matrix;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.util.inv.InternalInventoryHost;
import com.extendedae_plus.init.ModBlockEntities;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CrafterCorePlusBlockEntity extends TileAssemblerMatrixFunction implements InternalInventoryHost, IGridTickable {

    public CrafterCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get(),pos, blockState);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return null;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int i) {
        return null;
    }

    @Override
    public void add(ClusterAssemblerMatrix clusterAssemblerMatrix) {

    }

    @Override
    public void onChangeInventory(InternalInventory internalInventory, int i) {

    }
}
