package com.extendedae_plus.network;

import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：标签无线收发器操作（设置/删除/断开）。
 */
public record LabelNetworkActionC2SPacket(BlockPos pos, String label, Action action) implements CustomPacketPayload {
    public enum Action { SET, DELETE, DISCONNECT }

    public static final Type<LabelNetworkActionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "label_network_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelNetworkActionC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeUtf(pkt.label == null ? "" : pkt.label, 128);
                buf.writeEnum(pkt.action);
            },
            buf -> new LabelNetworkActionC2SPacket(buf.readBlockPos(), buf.readUtf(128), buf.readEnum(Action.class))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LabelNetworkActionC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            var level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!level.hasChunkAt(packet.pos)) return;
            var be = level.getBlockEntity(packet.pos);
            if (!(be instanceof LabeledWirelessTransceiverBlockEntity te)) return;

            switch (packet.action) {
                case SET -> te.applyLabel(packet.label);
                case DELETE -> {
                    String target = (packet.label == null || packet.label.isEmpty()) ? te.getLabelForDisplay() : packet.label;
                    if (target != null && !target.isEmpty()) {
                        LabelNetworkRegistry.get(level).removeNetwork(level, target, te.getPlacerId());
                    }
                    te.clearLabel();
                }
                case DISCONNECT -> te.clearLabel();
            }
        });
    }
}
