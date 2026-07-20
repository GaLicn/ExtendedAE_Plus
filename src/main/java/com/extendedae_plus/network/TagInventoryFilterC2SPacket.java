package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TagInventoryFilterC2SPacket(BlockPos pos, String whiteListExpression, String blackListExpression)
        implements CustomPacketPayload {

    public static final Type<TagInventoryFilterC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "tag_inventory_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TagInventoryFilterC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeUtf(packet.whiteListExpression == null ? "" : packet.whiteListExpression,
                        TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
                buf.writeUtf(packet.blackListExpression == null ? "" : packet.blackListExpression,
                        TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
            },
            buf -> new TagInventoryFilterC2SPacket(
                    buf.readBlockPos(),
                    buf.readUtf(TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH),
                    buf.readUtf(TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TagInventoryFilterC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof TagInventoryMEInterfaceBlockEntity tagInterface) {
                tagInterface.setTagFilters(packet.whiteListExpression, packet.blackListExpression);
            }
        });
    }
}
