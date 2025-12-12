package com.extendedae_plus.network;

import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridNode;
import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：请求标签网络列表及当前标签信息。
 */
public record LabelNetworkListC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LabelNetworkListC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "label_network_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelNetworkListC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBlockPos(pkt.pos),
            buf -> new LabelNetworkListC2SPacket(buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LabelNetworkListC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
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

            int usedChannels = 0;
            int maxChannels = 0;
            var node = te.getGridNode();
            if (node != null && node.isActive()) {
                for (var connection : node.getConnections()) {
                    usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
                }
                if (node instanceof GridNode gridNode) {
                    var channelMode = gridNode.getGrid().getPathingService().getChannelMode();
                    if (channelMode == ChannelMode.INFINITE) {
                        maxChannels = -1;
                    } else {
                        maxChannels = gridNode.getMaxChannels();
                    }
                }
            }

            ctx.reply(new LabelNetworkListS2CPacket(pkt.pos, list, currentLabel == null ? "" : currentLabel, ownerName, usedChannels, maxChannels, onlineCount));
        });
    }
}
