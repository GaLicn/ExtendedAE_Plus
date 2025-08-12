package com.extendedae_plus.content.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.extendedae_plus.init.ModBlockEntities;

public class WirelessTransceiverBlock extends Block implements EntityBlock {
    public WirelessTransceiverBlock(Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessTransceiverBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WirelessTransceiverBlockEntity te) {
            boolean sneaking = player.isShiftKeyDown();
            if (sneaking) {
                // 简单演示：Shift+右键 频率+1
                long f = te.getFrequency();
                te.setFrequency(f + 1);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Freq: " + te.getFrequency()), true);
            } else {
                te.setMasterMode(!te.isMasterMode());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(te.isMasterMode() ? "Mode: MASTER" : "Mode: SLAVE"), true);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessTransceiverBlockEntity te) {
                te.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.WIRELESS_TRANSCEIVER_BE.get()
                ? (lvl, pos, st, be) -> WirelessTransceiverBlockEntity.serverTick(lvl, pos, st, (WirelessTransceiverBlockEntity) be)
                : null;
    }
}
