package com.extendedae_plus.content.matrix.supermatrix;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.init.ModMenuTypes;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public abstract class SuperAssemblerMatrixBlock<T extends SuperAssemblerMatrixBlockEntity> extends AEBaseEntityBlock<T> {

    protected SuperAssemblerMatrixBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(BlockAssemblerMatrixBase.FORMED, false)
                .setValue(BlockAssemblerMatrixBase.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockAssemblerMatrixBase.FORMED);
        builder.add(BlockAssemblerMatrixBase.POWERED);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, T blockEntity) {
        var formed = blockEntity.eap$getSuperMatrixCluster() != null;
        var powered = formed && blockEntity.getMainNode().isActive();
        return currentState
                .setValue(BlockAssemblerMatrixBase.FORMED, formed)
                .setValue(BlockAssemblerMatrixBase.POWERED, powered);
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
            @NotNull BlockState neighborState, LevelAccessor level, @NotNull BlockPos pos,
            @NotNull BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof SuperAssemblerMatrixBlockEntity blockEntity) {
            blockEntity.requestModelDataUpdate();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            SuperAssemblerMatrixCalculator.recalculate(serverLevel, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (newState.getBlock() == state.getBlock()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part) {
            part.eap$breakSuperMatrixCluster();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @NotNull InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            ItemStack heldItem, BlockHitResult hitResult) {
        var blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity == null || blockEntity.eap$getSuperMatrixCluster() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && blockEntity.getMainNode().isActive()) {
            MenuOpener.open(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), player,
                    MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
