package com.extendedae_plus.content.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TagInventoryMEInterfaceBlock extends Block implements EntityBlock {

    public TagInventoryMEInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TagInventoryMEInterfaceBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return this.openMenu(level, pos, player).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return this.openMenu(level, pos, player);
    }

    private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TagInventoryMEInterfaceBlockEntity tagInterface
                    && blockEntity instanceof MenuProvider provider) {
                serverPlayer.openMenu(provider, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUtf(tagInterface.getWhiteListExpression(), TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
                    buf.writeUtf(tagInterface.getBlackListExpression(), TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
