package com.extendedae_plus.content.wireless;

import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 标签无线收发器方块（无交互 UI，占位实现）。
 * 取消所有徒手/道具调节，只允许右键打开后续 UI（当前仅占位返回 SUCCESS）。
 */
public class LabeledWirelessTransceiverBlock extends Block implements EntityBlock {
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 5);

    public LabeledWirelessTransceiverBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, 5));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabeledWirelessTransceiverBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LabeledWirelessTransceiverBlockEntity te) {
                te.setPlacerId(player.getUUID(), player.getName().getString());
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LabeledWirelessTransceiverBlockEntity te) {
            NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, te, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LabeledWirelessTransceiverBlockEntity te) {
                te.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // 与旧收发器保持一致：锁定时降低挖掘速度
        float baseProgress = super.getDestroyProgress(state, player, level, pos);
        if (level.getBlockEntity(pos) instanceof LabeledWirelessTransceiverBlockEntity te) {
            if (te.isLocked()) {
                return baseProgress * 0.1f;
            }
        }
        return baseProgress;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.LABELED_WIRELESS_TRANSCEIVER_BE.get()
                ? (lvl, pos, st, be) -> LabeledWirelessTransceiverBlockEntity.serverTick(lvl, pos, st, (LabeledWirelessTransceiverBlockEntity) be)
                : null;
    }
}
