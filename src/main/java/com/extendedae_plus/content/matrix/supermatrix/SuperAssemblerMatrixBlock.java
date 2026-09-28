package com.extendedae_plus.content.matrix.supermatrix;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.init.ModMenuTypes;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
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
    public @NotNull BlockState updateShape(@NotNull BlockState state, LevelReader level,
            @NotNull ScheduledTickAccess scheduledTickAccess, @NotNull BlockPos pos, @NotNull Direction direction,
            @NotNull BlockPos neighborPos, @NotNull BlockState neighborState, @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SuperAssemblerMatrixBlockEntity blockEntity) {
            blockEntity.requestModelDataUpdate();
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block block, @Nullable Orientation orientation, boolean isMoving) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            SuperAssemblerMatrixCalculator.scheduleAfterNeighborChange(serverLevel, pos, pos);
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        var blockEntity = this.getBlockEntity(level, pos);
        if (!this.isFormedForInteraction(state, blockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && blockEntity != null && blockEntity.getMainNode().isActive()) {
            MenuOpener.open(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), player,
                    MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        var parent = super.useItemOn(heldItem, state, level, pos, player, hand, hitResult);
        if (parent != InteractionResult.PASS) {
            return parent;
        }
        var blockEntity = this.getBlockEntity(level, pos);
        if (!this.isFormedForInteraction(state, blockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && blockEntity != null && blockEntity.getMainNode().isActive()) {
            MenuOpener.open(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), player,
                    MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.SUCCESS;
    }

    private boolean isFormedForInteraction(BlockState state, T blockEntity) {
        // 客户端以同步的方块状态为准，避免手持方块时先走默认放置预览再被服务端回滚。
        if (state.hasProperty(BlockAssemblerMatrixBase.FORMED) && state.getValue(BlockAssemblerMatrixBase.FORMED)) {
            return true;
        }
        return blockEntity != null && blockEntity.eap$getSuperMatrixCluster() != null;
    }
}
