package com.extendedae_plus.network.upload;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.api.upload.IPatternEncodingShiftUploadSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端在点击编码按钮时发送一次，携带当前是否按下 Shift。
 */
public record EncodeWithShiftFlagC2SPacket(boolean shiftDown) {

    public static void encode(EncodeWithShiftFlagC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.shiftDown());
    }

    public static EncodeWithShiftFlagC2SPacket decode(FriendlyByteBuf buf) {
        return new EncodeWithShiftFlagC2SPacket(buf.readBoolean());
    }

    public static void handle(EncodeWithShiftFlagC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu instanceof IPatternEncodingShiftUploadSync sync) {
                sync.eap$clientSetShiftUpload(msg.shiftDown());
            }
        });
        ctx.setPacketHandled(true);
    }
}
