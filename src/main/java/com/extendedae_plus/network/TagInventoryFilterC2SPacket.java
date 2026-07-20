package com.extendedae_plus.network;

import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TagInventoryFilterC2SPacket(BlockPos pos, String whiteListExpression, String blackListExpression) {

    public static void encode(TagInventoryFilterC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeUtf(packet.whiteListExpression == null ? "" : packet.whiteListExpression,
                TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
        buf.writeUtf(packet.blackListExpression == null ? "" : packet.blackListExpression,
                TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
    }

    public static TagInventoryFilterC2SPacket decode(FriendlyByteBuf buf) {
        return new TagInventoryFilterC2SPacket(
                buf.readBlockPos(),
                buf.readUtf(TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH),
                buf.readUtf(TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH));
    }

    public static void handle(TagInventoryFilterC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof TagInventoryMEInterfaceBlockEntity tagInterface) {
                tagInterface.setTagFilters(packet.whiteListExpression, packet.blackListExpression);
            }
        });
        context.setPacketHandled(true);
    }
}
