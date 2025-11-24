package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.network.ChannelCardBindPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 频道卡客户端事件处理器
 * 处理左键空气事件并发送网络包到服务端
 */
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public class ChannelCardClientHandler {

    /**
     * 左键空气事件（仅客户端）
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(InputEvent.InteractionKeyMappingTriggered event) {
        // 只处理左键空气（attack模式）
        if (!event.isAttack()) {
            return;
        }
        
        // 获取客户端玩家
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        
        // 只处理潜行
        if (!player.isShiftKeyDown()) {
            return;
        }
        
        // 检查是否手持频道卡
        InteractionHand hand = event.getHand();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != ModItems.CHANNEL_CARD.get()) {
            return;
        }
        
        // 发送网络包到服务端
        PacketDistributor.sendToServer(new ChannelCardBindPacket(hand));
    }
}

