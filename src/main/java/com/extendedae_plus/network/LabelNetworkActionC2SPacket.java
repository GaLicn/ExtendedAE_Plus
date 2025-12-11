package com.extendedae_plus.network;

import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 标签无线收发器的操作数据包（无UI，仅后端逻辑）。
 * 支持：设置/切换标签、删除标签、刷新重连。
 */
public class LabelNetworkActionC2SPacket {
    public enum Action {
        SET, DELETE, DISCONNECT
    }

    private final BlockPos pos;
    private final String label;
    private final Action action;

    public LabelNetworkActionC2SPacket(BlockPos pos, String label, Action action) {
        this.pos = pos;
        this.label = label;
        this.action = action;
    }

    public static void encode(LabelNetworkActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeUtf(packet.label == null ? "" : packet.label, 128);
        buf.writeEnum(packet.action);
    }

    public static LabelNetworkActionC2SPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String label = buf.readUtf(128);
        Action action = buf.readEnum(Action.class);
        return new LabelNetworkActionC2SPacket(pos, label, action);
    }

    public static void handle(LabelNetworkActionC2SPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var level = player.serverLevel();
            if (!level.hasChunkAt(packet.pos)) return;

            var be = level.getBlockEntity(packet.pos);
            if (!(be instanceof LabeledWirelessTransceiverBlockEntity te)) return;

            switch (packet.action) {
                case SET -> te.applyLabel(packet.label);
                case DELETE -> {
                    String target = packet.label == null || packet.label.isEmpty() ? te.getLabelForDisplay() : packet.label;
                    if (target != null && !target.isEmpty()) {
                        com.extendedae_plus.ae.wireless.LabelNetworkRegistry.get(level)
                                .removeNetwork(level, target, te.getPlacerId());
                    }
                    te.clearLabel();
                }
                case DISCONNECT -> te.clearLabel();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
