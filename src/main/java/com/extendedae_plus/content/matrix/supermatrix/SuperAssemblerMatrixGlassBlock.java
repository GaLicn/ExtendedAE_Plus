package com.extendedae_plus.content.matrix.supermatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class SuperAssemblerMatrixGlassBlock extends SuperAssemblerMatrixWallBlock<SuperAssemblerMatrixGlassBlockEntity> {

    public SuperAssemblerMatrixGlassBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter blockGetter,
            @NotNull BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter blockGetter,
            @NotNull BlockPos pos) {
        return true;
    }

}
