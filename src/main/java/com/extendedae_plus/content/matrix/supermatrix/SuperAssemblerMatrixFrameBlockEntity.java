package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SuperAssemblerMatrixFrameBlockEntity extends SuperAssemblerMatrixBlockEntity {

    public SuperAssemblerMatrixFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPER_ASSEMBLER_MATRIX_FRAME_BE.get(), pos, state);
    }
}
