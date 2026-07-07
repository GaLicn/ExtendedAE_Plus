package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SuperAssemblerMatrixGlassBlockEntity extends SuperAssemblerMatrixBlockEntity {

    public SuperAssemblerMatrixGlassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPER_ASSEMBLER_MATRIX_GLASS_BE.get(), pos, state);
    }
}
