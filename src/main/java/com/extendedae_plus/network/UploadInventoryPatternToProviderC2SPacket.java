package com.extendedae_plus.network;

import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 从样板访问终端上传玩家背包中的编码样板到指定的样板供应器。
 * 适用于 ExtendedAE 的 GuiExPatternTerminal 或 AE2 的 PatternAccessTermScreen。
 */
public class UploadInventoryPatternToProviderC2SPacket implements CustomPacketPayload {
    public static final Type<UploadInventoryPatternToProviderC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "upload_inventory_pattern_to_provider"));

    public static final StreamCodec<FriendlyByteBuf, UploadInventoryPatternToProviderC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.playerSlotIndex);
                buf.writeLong(pkt.providerId);
            },
            buf -> new UploadInventoryPatternToProviderC2SPacket(buf.readVarInt(), buf.readLong())
    );

    private final int playerSlotIndex;
    private final long providerId;

    public UploadInventoryPatternToProviderC2SPacket(int playerSlotIndex, long providerId) {
        this.playerSlotIndex = playerSlotIndex;
        this.providerId = providerId;
    }

    public static void handle(final UploadInventoryPatternToProviderC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ExtendedAEPatternUploadUtil.uploadPatternToProvider(player, msg.playerSlotIndex, msg.providerId);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
