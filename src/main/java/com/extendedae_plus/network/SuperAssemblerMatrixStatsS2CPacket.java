package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SuperAssemblerMatrixStatsS2CPacket(long concurrentExecutions) implements CustomPacketPayload {

    public static final Type<SuperAssemblerMatrixStatsS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ExtendedAEPlus.MODID, "super_assembler_matrix_stats"));

    public static final StreamCodec<FriendlyByteBuf, SuperAssemblerMatrixStatsS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarLong(packet.concurrentExecutions),
            buf -> new SuperAssemblerMatrixStatsS2CPacket(buf.readVarLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
