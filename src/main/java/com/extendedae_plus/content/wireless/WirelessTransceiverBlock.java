package com.extendedae_plus.content.wireless;

import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class WirelessTransceiverBlock extends Block implements EntityBlock {
    public WirelessTransceiverBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessTransceiverBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessTransceiverBlockEntity te) {
                te.setPlacerId(player.getUUID(), player.getName().getString());
            }
        }
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessTransceiverBlockEntity te) {
                ItemStack mainHand = player.getMainHandItem();
                
                // 潜行左键频道卡：写入频道卡信息到收发器
                if (player.isShiftKeyDown() && mainHand.getItem() == ModItems.CHANNEL_CARD.get()) {
                    handleChannelCardBinding(te, mainHand, player);
                    super.attack(state, level, pos, player);
                    return;
                }
                
                // 潜行左键（其他物品）：减频（-1 或 -10）
                if (player.isShiftKeyDown()) {
                    if (te.isLocked()) {
                        player.displayClientMessage(Component.literal("收发器已锁定，无法修改频道"), true);
                        super.attack(state, level, pos, player);
                        return;
                    }
                    int step = 1;
                    if (mainHand.is(Items.REDSTONE_TORCH)) step = 10;
                    if (mainHand.is(Items.STICK)) step = 10;
                    long f = te.getFrequency();
                    f -= step;
                    if (f < 0) f = 0;
                    te.setFrequency(f);
                    player.displayClientMessage(Component.literal("频道：" + te.getFrequency()), true);
                }
            }
        }
        super.attack(state, level, pos, player);
    }
    
    /**
     * 处理频道卡绑定到收发器
     */
    private void handleChannelCardBinding(WirelessTransceiverBlockEntity te, ItemStack channelCard, Player player) {
        UUID cardOwner = ChannelCardItem.getOwnerUUID(channelCard);
        
        if (cardOwner != null) {
            // 写入频道卡的所有者到收发器
            String teamName = ChannelCardItem.getTeamName(channelCard);
            te.setPlacerId(cardOwner, teamName);
            player.displayClientMessage(
                Component.literal("已将收发器绑定至：" + (teamName != null ? teamName : cardOwner.toString().substring(0, 8))), 
                true
            );
        } else {
            // 频道卡未绑定所有者，使用当前玩家
            te.setPlacerId(player.getUUID(), player.getName().getString());
            player.displayClientMessage(Component.literal("频道卡未绑定，已使用当前玩家"), true);
        }
    }

    // 1.21+: 拆分为 useItemOn 与 useWithoutItem
    @Override
    protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WirelessTransceiverBlockEntity te)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        boolean sneaking = player.isShiftKeyDown();
        if (sneaking) {
            if (te.isLocked()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("收发器已锁定，无法修改频道"), true);
                }
            } else {
                int step = 1;
                if (heldItem.is(Items.REDSTONE_TORCH)) step = 10;
                if (heldItem.is(Items.STICK)) step = 10;

                long f = te.getFrequency();
                if (hand == InteractionHand.MAIN_HAND) {
                    f += step;
                } else {
                    f -= step;
                    if (f < 0) f = 0;
                }
                te.setFrequency(f);
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("频道：" + te.getFrequency()), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (te.isLocked()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("收发器已锁定，无法切换模式"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            te.setMasterMode(!te.isMasterMode());
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(te.isMasterMode() ? "模式：主端" : "模式：从端"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WirelessTransceiverBlockEntity te)) {
            return InteractionResult.PASS;
        }
        boolean sneaking = player.isShiftKeyDown();
        if (sneaking) {
            if (te.isLocked()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("收发器已锁定，无法修改频道"), true);
                }
            } else {
                long f = te.getFrequency();
                // 空手交互：按主手逻辑 +1
                f += 1;
                te.setFrequency(f);
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("频道：" + te.getFrequency()), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (te.isLocked()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("收发器已锁定，无法切换模式"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            te.setMasterMode(!te.isMasterMode());
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(te.isMasterMode() ? "模式：主端" : "模式：从端"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
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
