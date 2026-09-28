package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.init.ModBlocks;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuperAssemblerMatrixFrameBlock extends SuperAssemblerMatrixBlock<SuperAssemblerMatrixFrameBlockEntity> {

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public SuperAssemblerMatrixFrameBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SHAPE, Shape.block));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getShapeType(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, LevelReader level,
            @NotNull ScheduledTickAccess scheduledTickAccess, @NotNull BlockPos pos, @NotNull Direction direction,
            @NotNull BlockPos neighborPos, @NotNull BlockState neighborState, @NotNull RandomSource random) {
        var updatedState = super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos,
                neighborState, random);
        return this.getShapeType(updatedState, level, pos);
    }

    private BlockState getShapeType(BlockState baseState, LevelReader level, BlockPos pos) {
        var type = Shape.block;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        boolean xx = isFrame(level, x - 1, y, z) && isFrame(level, x + 1, y, z);
        boolean yy = isFrame(level, x, y - 1, z) && isFrame(level, x, y + 1, z);
        boolean zz = isFrame(level, x, y, z - 1) && isFrame(level, x, y, z + 1);

        if (xx && !yy && !zz) {
            type = Shape.column_x;
        } else if (!xx && yy && !zz) {
            type = Shape.column_y;
        } else if (!xx && !yy && zz) {
            type = Shape.column_z;
        }
        return baseState.setValue(SHAPE, type)
                .setValue(BlockAssemblerMatrixBase.FORMED, baseState.getValue(BlockAssemblerMatrixBase.FORMED))
                .setValue(BlockAssemblerMatrixBase.POWERED, baseState.getValue(BlockAssemblerMatrixBase.POWERED));
    }

    private static boolean isFrame(LevelReader level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get());
    }

    public enum Shape implements StringRepresentable {
        block, column_x, column_y, column_z;

        @Override
        public @NotNull String getSerializedName() {
            return this.name();
        }
    }
}
