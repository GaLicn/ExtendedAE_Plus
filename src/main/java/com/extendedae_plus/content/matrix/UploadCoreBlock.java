package com.extendedae_plus.content.matrix;

import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * ExtendedAE_Plus: 装配矩阵上传核心方块（内部功能块）。
 * 仅用于作为多方块内部的"功能块"存在；是否允许自动上传由工具类检查集群中是否存在该核心决定。
 */
public class UploadCoreBlock extends BlockAssemblerMatrixBase<UploadCoreBlockEntity> {

    public UploadCoreBlock() {
        super();
    }

    public UploadCoreBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public Item getPresentItem() {
        // 由对应的 BlockItem 注册返回，上传核心不需要特殊的 PresentItem，可返回自身的 BlockItem
        return com.extendedae_plus.init.ModItems.ASSEMBLER_MATRIX_UPLOAD_CORE.get();
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
