package com.extendedae_plus.content.ae2;

import appeng.api.implementations.items.IMemoryCard;
import appeng.block.crafting.PatternProviderBlock;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MirrorPatternProviderBlock extends PatternProviderBlock {

    public MirrorPatternProviderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        var mirror = this.getMirror(level, pos);
        if (mirror == null) {
            return InteractionResult.PASS;
        }

        if (InteractionUtil.canWrenchRotate(heldItem) || heldItem.getItem() instanceof IMemoryCard) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(
                        Component.translatable("extendedae_plus.message.mirror_pattern_provider.readonly"));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        var mirror = this.getMirror(level, pos);
        if (mirror == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            player.sendSystemMessage(mirror.getStatusMessage());
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    private MirrorPatternProviderBlockEntity getMirror(Level level, BlockPos pos) {
        var blockEntity = this.getBlockEntity(level, pos);
        return blockEntity instanceof MirrorPatternProviderBlockEntity mirror ? mirror : null;
    }
}
