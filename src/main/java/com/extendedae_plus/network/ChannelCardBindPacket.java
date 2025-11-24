package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.materials.ChannelCardItem;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 频道卡绑定网络包
 * 客户端发送到服务端，用于处理左键空气的绑定/解绑操作
 */
public class ChannelCardBindPacket implements CustomPacketPayload {
    
    public static final Type<ChannelCardBindPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "channel_card_bind"));

    public static final StreamCodec<FriendlyByteBuf, ChannelCardBindPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeEnum(pkt.hand),
            buf -> new ChannelCardBindPacket(buf.readEnum(InteractionHand.class))
    );

    private final InteractionHand hand;
    
    public ChannelCardBindPacket(InteractionHand hand) {
        this.hand = hand;
    }

    public static void handle(final ChannelCardBindPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = player.getItemInHand(msg.hand);
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
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

