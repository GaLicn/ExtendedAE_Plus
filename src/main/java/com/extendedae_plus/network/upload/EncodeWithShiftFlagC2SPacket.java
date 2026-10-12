package com.extendedae_plus.network.upload;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync.EncodeRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：图样编码终端点击「编码」按钮时上报本次编码请求，服务端在随后的 {@code encode()} 中消费。
 *
 * <p>Shift 状态与唯一匹配判定均依赖客户端环境，故由客户端上报（见 {@link EncodeRequest}）。</p>
 */
public record EncodeWithShiftFlagC2SPacket(EncodeRequest request) {

    public static void encode(EncodeWithShiftFlagC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.request().shiftDown());
        buf.writeNullable(msg.request().uniqueProvider(), FriendlyByteBuf::writeUtf);
    }

    public static EncodeWithShiftFlagC2SPacket decode(FriendlyByteBuf buf) {
        boolean shiftDown = buf.readBoolean();
        String provider = buf.readNullable(FriendlyByteBuf::readUtf);
        return new EncodeWithShiftFlagC2SPacket(new EncodeRequest(shiftDown, provider));
    }

    public static void handle(EncodeWithShiftFlagC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu instanceof IPatternEncodeRequestSync sync) {
                sync.eap$clientSetEncodeRequest(msg.request());
            }
        });
        ctx.setPacketHandled(true);
    }
}
