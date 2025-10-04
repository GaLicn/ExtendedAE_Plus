package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SPacketEncodeFinished() implements CustomPacketPayload {
    public static final Type<C2SPacketEncodeFinished> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "encode_finished"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final C2SPacketEncodeFinished INSTANCE = new C2SPacketEncodeFinished();

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPacketEncodeFinished> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(final C2SPacketEncodeFinished packet, final IPayloadContext context) {
        if (context.player().level().isClientSide) PacketDistributor.sendToServer(RequestUploadingC2SPacket.INSTANCE);
    }
}
