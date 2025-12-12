package com.extendedae_plus.network;

import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
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
            String ownerName = te.getPlacerId() != null ? WirelessTeamUtil.getNetworkOwnerName(level, te.getPlacerId()).getString() : "";

            int onlineCount = 0;
            if (currentLabel != null && !currentLabel.isEmpty()) {
                var network = LabelNetworkRegistry.get(level).getNetwork(level, currentLabel, te.getPlacerId());
                if (network != null) {
                    onlineCount = network.endpointCount();
                }
            }

            // 计算频道占用信息（与 Jade 显示一致）
            int usedChannels = 0;
            int maxChannels = 0;
            var node = te.getGridNode();
            if (node != null && node.isActive()) {
                for (var connection : node.getConnections()) {
                    usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
                }
                if (node instanceof appeng.me.GridNode gridNode) {
                    var channelMode = gridNode.getGrid().getPathingService().getChannelMode();
                    if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
                        maxChannels = -1;
                    } else {
                        maxChannels = gridNode.getMaxChannels();
                    }
                }
            }

            LabelNetworkListS2CPacket rsp = new LabelNetworkListS2CPacket(pkt.pos, list, currentLabel, ownerName, usedChannels, maxChannels, onlineCount);
            com.extendedae_plus.init.ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), rsp);
        });
        ctx.get().setPacketHandled(true);
    }
}
