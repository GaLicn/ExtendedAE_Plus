package com.extendedae_plus.network;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 指示客户端对某个 AEKey 的样板进行高亮/取消高亮（仅作用于接收该包的客户端）
 */
public class SetPatternHighlightS2CPacket {
    private final AEKey key;
    private final boolean highlight;

    public SetPatternHighlightS2CPacket(AEKey key, boolean highlight) {
        this.key = key;
        this.highlight = highlight;
    }

    public static void encode(SetPatternHighlightS2CPacket msg, FriendlyByteBuf buf) {
        AEKey.writeKey(buf, msg.key);
        buf.writeBoolean(msg.highlight);
    }

    public static SetPatternHighlightS2CPacket decode(FriendlyByteBuf buf) {
        AEKey key = AEKey.readKey(buf);
        boolean h = buf.readBoolean();
        return new SetPatternHighlightS2CPacket(key, h);
    }

    public static void handle(SetPatternHighlightS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ClientPatternHighlightStore.setHighlight(msg.key, msg.highlight);
            } catch (Throwable ignored) {
            }
        });
        ctx.setPacketHandled(true);
    }
}


