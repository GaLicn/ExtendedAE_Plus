package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModBlockEntities;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SpeedCorePlusBlockEntity extends TileAssemblerMatrixFunction {

    public SpeedCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ASSEMBLER_MATRIX_SPEED_PLUS_BE.get(), pos, blockState);
    }

    @Override
    public void add(ClusterAssemblerMatrix cluster) {
        if (cluster == null) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            cluster.addSpeedCore();
        }
    }
}
