package com.extendedae_plus.content.ae2;

import appeng.api.implementations.items.IMemoryCard;
import appeng.block.crafting.PatternProviderBlock;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MirrorPatternProviderBlock extends PatternProviderBlock {

    public MirrorPatternProviderBlock() {
        super();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        var mirror = this.getMirror(level, pos);
        if (mirror == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (InteractionUtil.canWrenchRotate(heldItem) || heldItem.getItem() instanceof IMemoryCard) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("extendedae_plus.message.mirror_pattern_provider.readonly"),
                        true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        var mirror = this.getMirror(level, pos);
        if (mirror == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (mirror.tryBindToAdjacentMaster()) {
                    player.displayClientMessage(mirror.createBoundMessage(), true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master"),
                            true);
                }
            } else {
                player.displayClientMessage(mirror.getStatusMessage(), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    private MirrorPatternProviderBlockEntity getMirror(Level level, BlockPos pos) {
        var blockEntity = this.getBlockEntity(level, pos);
        return blockEntity instanceof MirrorPatternProviderBlockEntity mirror ? mirror : null;
    }
}
