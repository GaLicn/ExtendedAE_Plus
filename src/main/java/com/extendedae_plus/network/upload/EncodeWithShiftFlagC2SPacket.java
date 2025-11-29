package com.extendedae_plus.network.upload;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.upload.IPatternEncodingShiftUploadSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：图样编码终端点击「编码」按钮时同步客户端的 Shift 状态。
 */
public class EncodeWithShiftFlagC2SPacket implements CustomPacketPayload {

    public static final Type<EncodeWithShiftFlagC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "encode_with_shift"));

    public static final StreamCodec<FriendlyByteBuf, EncodeWithShiftFlagC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBoolean(pkt.shiftDown),
            buf -> new EncodeWithShiftFlagC2SPacket(buf.readBoolean())
    );

    private final boolean shiftDown;

    public EncodeWithShiftFlagC2SPacket(boolean shiftDown) {
        this.shiftDown = shiftDown;
    }

    public boolean shiftDown() {
        return shiftDown;
    }

    public static void handle(final EncodeWithShiftFlagC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu instanceof IPatternEncodingShiftUploadSync sync) {
                sync.eap$clientSetShiftUpload(msg.shiftDown());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
