package com.extendedae_plus.network;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 请求将图样编码终端的已编码样板上传到指定的样板供应器（由客户端选择）。
 */
public class UploadEncodedPatternToProviderC2SPacket implements CustomPacketPayload {
    public static final Type<UploadEncodedPatternToProviderC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "upload_pattern_to_provider"));

    public static final StreamCodec<FriendlyByteBuf, UploadEncodedPatternToProviderC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeLong(pkt.providerId),
            buf -> new UploadEncodedPatternToProviderC2SPacket(buf.readLong())
    );
    private final long providerId;

    public UploadEncodedPatternToProviderC2SPacket(long providerId) {
        this.providerId = providerId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UploadEncodedPatternToProviderC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) return;
            // 支持两种模式：
            // 1) providerId >= 0: 访问终端 byId 模式
            // 2) providerId < 0:   索引模式（由列表回退路径生成），index = -1 - providerId
            if (msg.providerId >= 0) {
                ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToProvider(player, menu, msg.providerId);
            } else {
                int index = (int) (-1L - msg.providerId);
                ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToProviderByIndex(player, menu, index);
            }
        });
    }
}
