package com.extendedae_plus.content.matrix.supermatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class SuperAssemblerMatrixGlassBlock extends SuperAssemblerMatrixWallBlock<SuperAssemblerMatrixGlassBlockEntity> {

    public SuperAssemblerMatrixGlassBlock(Properties properties) {
        super(properties.noOcclusion().isViewBlocking((state, level, pos) -> false));
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

    @Override
    protected @NotNull VoxelShape getVisualShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacentState,
            @NotNull Direction direction) {
        if (adjacentState.is(this) && adjacentState.getRenderShape() == state.getRenderShape()) {
            return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }
}
