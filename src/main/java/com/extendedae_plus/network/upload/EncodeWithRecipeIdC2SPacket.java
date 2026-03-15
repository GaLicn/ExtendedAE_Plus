package com.extendedae_plus.network.upload;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.upload.IPatternEncodingIdSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：图样编码终端点击「编码」按钮时上传客户端的配方ID。
 */
public class EncodeWithRecipeIdC2SPacket implements CustomPacketPayload {

    public static final Type<EncodeWithRecipeIdC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "encode_with_recipe"));

    public static final StreamCodec<FriendlyByteBuf, EncodeWithRecipeIdC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeResourceLocation(pkt.id),
            buf -> new EncodeWithRecipeIdC2SPacket(buf.readResourceLocation())
    );

    private final ResourceLocation id;

    public EncodeWithRecipeIdC2SPacket(ResourceLocation id) {
        this.id = id;
    }

    public static void handle(final EncodeWithRecipeIdC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu instanceof IPatternEncodingIdSync sync) {
                sync.eap$clientRecipeIdUpload(msg.id);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
