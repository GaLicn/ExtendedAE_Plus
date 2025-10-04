package com.extendedae_plus.hooks;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.util.InteractionUtil;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ExtendedAEPlus.MODID)
public final class WrenchHook {
    private WrenchHook() {}

    @SubscribeEvent
    public static void onPlayerUseBlockEvent(PlayerInteractEvent.RightClickBlock event) {
        // 1.21 NeoForge: 避免依赖 Event.Result，使用 isCanceled() 进行早退检查
        if (event.isCanceled()) {
            return;
        }
        var player = event.getEntity();
        var level = event.getLevel();
        var hand = event.getHand();
        var hit = event.getHitVec();

        // 仅主手、非旁观者
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        // 潜行且为扳手：拆解
        if (InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchDisassemble(stack)) {
            BlockEntity be = level.getBlockEntity(hit.getBlockPos());
            if (be instanceof WirelessTransceiverBlockEntity te) {
                var pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                var block = state.getBlock();

                if (!level.isClientSide) {
                    var drops = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) level, pos, te, player, stack);
                    for (var item : drops) {
                        player.getInventory().placeItemBackInInventory(item);
                    }
                }

                level.playSound(player, hit.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);

                block.playerWillDestroy(level, hit.getBlockPos(), state, player);
                level.removeBlock(hit.getBlockPos(), false);
                block.destroy(level, hit.getBlockPos(), state);

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            }
            // AE2 并行处理器系列（CraftingUnitBlock）潜行扳手拆除：直接入背包
            else {
                var pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof CraftingUnitBlock) {
                    if (!level.isClientSide) {
                        var drops = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) level, pos, level.getBlockEntity(pos), player, stack);
                        for (var item : drops) {
                            player.getInventory().placeItemBackInInventory(item);
                        }
                    }

                    level.playSound(player, hit.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);

                    state.getBlock().playerWillDestroy(level, pos, state, player);
                    level.removeBlock(pos, false);
                    state.getBlock().destroy(level, pos, state);

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
                }
            }
        } else if (!InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchRotate(stack)) {
            // 未潜行 + 扳手：切换锁定状态
            BlockEntity be = level.getBlockEntity(hit.getBlockPos());
            if (be instanceof WirelessTransceiverBlockEntity te) {
                // 仅在服务端切换与同步，避免仅客户端生效导致看起来“无效果”
                if (!level.isClientSide) {
                    boolean newLocked = !te.isLocked();
                    te.setLocked(newLocked);
                    // 同步方块更新到客户端
                    var pos = hit.getBlockPos();
                    BlockState state = level.getBlockState(pos);
                    try {
                        level.sendBlockUpdated(pos, state, state, 3);
                    } catch (Throwable t) {
                        ExtendedAEPlus.LOGGER.debug("sendBlockUpdated failed: {}", t.toString());
                    }
                    // 提示玩家（服务端消息下发到客户端）
                    player.displayClientMessage(Component.literal(newLocked ? "已锁定收发器" : "已解锁收发器"), true);
                    // 轻微反馈音效
                    level.playSound(player, hit.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, newLocked ? 0.6F : 0.9F);
                    ExtendedAEPlus.LOGGER.debug("Wrench toggle lock at {} -> {}", pos, newLocked);
                } else {
                    ExtendedAEPlus.LOGGER.debug("Client received wrench toggle intent (no-op on client)");
                }

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            }
        }
    }
}
