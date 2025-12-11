package com.extendedae_plus.network;

import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.client.screen.LabeledWirelessTransceiverScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 标签网络列表下发（服务端 -> 客户端）。
 */
public class LabelNetworkListS2CPacket {
    private final BlockPos pos;
    private final List<LabelNetworkRegistry.LabelNetworkSnapshot> list;
    private final String currentLabel;
    private final String ownerName;
    private final int usedChannels;
    private final int maxChannels;

    public LabelNetworkListS2CPacket(BlockPos pos, List<LabelNetworkRegistry.LabelNetworkSnapshot> list, String currentLabel, String ownerName, int usedChannels, int maxChannels) {
        this.pos = pos;
        this.list = list;
        this.currentLabel = currentLabel;
        this.ownerName = ownerName;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
    }

    public static void encode(LabelNetworkListS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.currentLabel == null ? "" : pkt.currentLabel, 128);
        buf.writeUtf(pkt.ownerName == null ? "" : pkt.ownerName, 128);
        buf.writeVarInt(pkt.usedChannels);
        buf.writeVarInt(pkt.maxChannels);
        buf.writeVarInt(pkt.list.size());
        for (LabelNetworkRegistry.LabelNetworkSnapshot s : pkt.list) {
            buf.writeUtf(s.label(), 128);
            buf.writeLong(s.channel());
        }
    }

    public static LabelNetworkListS2CPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String curLabel = buf.readUtf(128);
        String ownerName = buf.readUtf(128);
        int usedChannels = buf.readVarInt();
        int maxChannels = buf.readVarInt();
        int size = buf.readVarInt();
        List<LabelNetworkRegistry.LabelNetworkSnapshot> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String label = buf.readUtf(128);
            long channel = buf.readLong();
            list.add(new LabelNetworkRegistry.LabelNetworkSnapshot(label, channel));
        }
        return new LabelNetworkListS2CPacket(pos, list, curLabel, ownerName, usedChannels, maxChannels);
    }

    public static void handle(LabelNetworkListS2CPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(pkt));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(LabelNetworkListS2CPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LabeledWirelessTransceiverScreen screen && screen.isFor(pkt.pos)) {
            screen.updateList(pkt.list, pkt.currentLabel, pkt.ownerName, pkt.usedChannels, pkt.maxChannels);
        }
    }
}
