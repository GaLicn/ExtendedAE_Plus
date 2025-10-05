package com.extendedae_plus.hooks;

import appeng.util.InteractionUtil;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ui.FrequencyInputScreen;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import appeng.block.crafting.CraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID)
public final class WrenchHook {
    private WrenchHook() {}

    @SubscribeEvent
    public static void onPlayerUseBlockEvent(PlayerInteractEvent.RightClickBlock event) {
        if (event.getUseBlock() == Event.Result.DENY) {
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
            // 未潜行 + 扳手：打开频率输入界面
            BlockEntity be = level.getBlockEntity(hit.getBlockPos());
            if (be instanceof WirelessTransceiverBlockEntity te) {
                if (level.isClientSide) {
                    // 客户端打开GUI
                    openFrequencyInputScreen(hit.getBlockPos(), te.getFrequency());
                }
                // 轻微反馈音效
                level.playSound(player, hit.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 0.5F, 1.0F);

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void openFrequencyInputScreen(net.minecraft.core.BlockPos pos, long currentFrequency) {
        Minecraft.getInstance().setScreen(new FrequencyInputScreen(pos, currentFrequency));
    }
}
