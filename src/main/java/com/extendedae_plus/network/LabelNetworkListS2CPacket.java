package com.extendedae_plus.network;

import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.client.screen.LabeledWirelessTransceiverScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：标签网络列表下发。
 */
public record LabelNetworkListS2CPacket(BlockPos pos,
                                        List<LabelNetworkRegistry.LabelNetworkSnapshot> list,
                                        String currentLabel,
                                        String ownerName,
                                        int usedChannels,
                                        int maxChannels,
                                        int onlineCount) implements CustomPacketPayload {
    public static final Type<LabelNetworkListS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "label_network_list_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelNetworkListS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeUtf(pkt.currentLabel == null ? "" : pkt.currentLabel, 128);
                buf.writeUtf(pkt.ownerName == null ? "" : pkt.ownerName, 128);
                buf.writeVarInt(pkt.usedChannels);
                buf.writeVarInt(pkt.maxChannels);
                buf.writeVarInt(pkt.onlineCount);
                buf.writeVarInt(pkt.list.size());
                for (var s : pkt.list) {
                    buf.writeUtf(s.label(), 128);
                    buf.writeLong(s.channel());
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                String curLabel = buf.readUtf(128);
                String ownerName = buf.readUtf(128);
                int usedChannels = buf.readVarInt();
                int maxChannels = buf.readVarInt();
                int onlineCount = buf.readVarInt();
                int size = buf.readVarInt();
                List<LabelNetworkRegistry.LabelNetworkSnapshot> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    String label = buf.readUtf(128);
                    long channel = buf.readLong();
                    list.add(new LabelNetworkRegistry.LabelNetworkSnapshot(label, channel));
                }
                return new LabelNetworkListS2CPacket(pos, list, curLabel, ownerName, usedChannels, maxChannels, onlineCount);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LabelNetworkListS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(pkt));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(LabelNetworkListS2CPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LabeledWirelessTransceiverScreen screen && screen.isFor(pkt.pos)) {
            screen.updateList(pkt.list, pkt.currentLabel, pkt.ownerName, pkt.usedChannels, pkt.maxChannels, pkt.onlineCount);
        }
    }
}
