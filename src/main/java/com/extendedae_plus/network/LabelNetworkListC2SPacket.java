package com.extendedae_plus.network;

import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 请求标签网络列表（客户端 -> 服务端）。
 */
public class LabelNetworkListC2SPacket {
    private final BlockPos pos;

    public LabelNetworkListC2SPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(LabelNetworkListC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static LabelNetworkListC2SPacket decode(FriendlyByteBuf buf) {
        return new LabelNetworkListC2SPacket(buf.readBlockPos());
    }

    public static void handle(LabelNetworkListC2SPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var level = player.serverLevel();
            if (!level.hasChunkAt(pkt.pos)) return;
            var be = level.getBlockEntity(pkt.pos);
            if (!(be instanceof LabeledWirelessTransceiverBlockEntity te)) return;

            var list = LabelNetworkRegistry.get(level).listNetworks(level, te.getPlacerId());
            String currentLabel = te.getLabelForDisplay();
            long currentChannel = te.getFrequency();
            LabelNetworkListS2CPacket rsp = new LabelNetworkListS2CPacket(pkt.pos, list, currentLabel, currentChannel);
            com.extendedae_plus.init.ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), rsp);
        });
        ctx.get().setPacketHandled(true);
    }
}
