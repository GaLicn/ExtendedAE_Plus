package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.SuperAssemblerMatrixScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SuperAssemblerMatrixStatsS2CPacket(long concurrentExecutions) implements CustomPacketPayload {

    public static final Type<SuperAssemblerMatrixStatsS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "super_assembler_matrix_stats"));

    public static final StreamCodec<FriendlyByteBuf, SuperAssemblerMatrixStatsS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarLong(packet.concurrentExecutions),
            buf -> new SuperAssemblerMatrixStatsS2CPacket(buf.readVarLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuperAssemblerMatrixStatsS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(SuperAssemblerMatrixStatsS2CPacket packet) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.setConcurrentExecutions(packet.concurrentExecutions);
        }
    }
}
