package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.menu.SuperAssemblerMatrixMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SuperAssemblerMatrixActionC2SPacket(String action) implements CustomPacketPayload {

    public static final Type<SuperAssemblerMatrixActionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "super_assembler_matrix_action"));

    public static final StreamCodec<FriendlyByteBuf, SuperAssemblerMatrixActionC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.action, 32),
            buf -> new SuperAssemblerMatrixActionC2SPacket(buf.readUtf(32))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuperAssemblerMatrixActionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof SuperAssemblerMatrixMenu menu) {
                menu.handleAction(packet.action);
            }
        });
    }
}
