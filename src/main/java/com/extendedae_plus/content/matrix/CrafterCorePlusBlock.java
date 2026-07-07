package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class CrafterCorePlusBlock extends BlockAssemblerMatrixBase<CrafterCorePlusBlockEntity> {

    public CrafterCorePlusBlock(Properties props) {
        super(props);
    }

    @Override
    public Item getPresentItem() {
        return ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get();
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            SuperAssemblerMatrixCalculator.recalculate(serverLevel, pos);
        }
    }
}
