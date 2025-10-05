package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.ChannelCardBindPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 频道卡客户端事件处理器
 * 处理左键空气事件并发送网络包到服务端
 */
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChannelCardClientHandler {

    /**
     * 左键空气事件（仅客户端）
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        
        // 只处理潜行
        if (!player.isShiftKeyDown()) {
            return;
        }
        
        // 检查是否手持频道卡
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != ModItems.CHANNEL_CARD.get()) {
            return;
        }
        
        // 发送网络包到服务端
        InteractionHand hand = event.getHand();
        ModNetwork.CHANNEL.sendToServer(new ChannelCardBindPacket(hand));
    }
}

