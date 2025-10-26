package com.extendedae_plus.network;

import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 频道卡绑定网络包
 * 客户端发送到服务端，用于处理左键空气的绑定/解绑操作
 */
public class ChannelCardBindPacket {
    
    private final InteractionHand hand;
    
    public ChannelCardBindPacket(InteractionHand hand) {
        this.hand = hand;
    }
    
    public static void encode(ChannelCardBindPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.hand);
    }
    
    public static ChannelCardBindPacket decode(FriendlyByteBuf buf) {
        return new ChannelCardBindPacket(buf.readEnum(InteractionHand.class));
    }
    
    public static void handle(ChannelCardBindPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            
            ItemStack stack = player.getItemInHand(packet.hand);
            if (stack.getItem() != ModItems.CHANNEL_CARD.get()) {
                return;
            }
            
            ServerLevel level = player.serverLevel();
            UUID currentOwner = ChannelCardItem.getOwnerUUID(stack);
            
            if (currentOwner != null) {
                // 已有所有者，清除
                ChannelCardItem.clearOwner(stack);
                player.displayClientMessage(
                    Component.translatable("item.extendedae_plus.channel_card.owner.cleared"), 
                    true
                );
            } else {
                // 写入当前玩家的UUID和团队信息
                UUID playerUUID = player.getUUID();
                ChannelCardItem.setOwnerUUID(stack, playerUUID);
                
                // 获取团队名称用于显示
                Component teamName = WirelessTeamUtil.getNetworkOwnerName(level, playerUUID);
                ChannelCardItem.setTeamName(stack, teamName.getString());
                
                player.displayClientMessage(
                    Component.translatable("item.extendedae_plus.channel_card.owner.bound", teamName), 
                    true
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

