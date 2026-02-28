package com.extendedae_plus.network;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: Request uploading an encoded pattern to a selected provider.
 */
public class UploadEncodedPatternToProviderC2SPacket {
    private final long providerId;

    public UploadEncodedPatternToProviderC2SPacket(long providerId) {
        this.providerId = providerId;
    }

    public static void encode(UploadEncodedPatternToProviderC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.providerId);
    }

    public static UploadEncodedPatternToProviderC2SPacket decode(FriendlyByteBuf buf) {
        return new UploadEncodedPatternToProviderC2SPacket(buf.readLong());
    }

    public static void handle(UploadEncodedPatternToProviderC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Prefer pending Ctrl+Q pattern upload when present.
            if (ProviderUploadUtil.hasPendingCtrlQPattern(player)) {
                if (ProviderUploadUtil.uploadPendingCtrlQPattern(player, msg.providerId)) {
                    return;
                }
            }

            if (player.containerMenu instanceof PatternEncodingTermMenu menu) {
                // 1) providerId >= 0: byId mode from access terminal
                // 2) providerId < 0: index mode, index = -1 - providerId
                if (msg.providerId >= 0) {
                    ProviderUploadUtil.uploadFromEncodingMenuToProvider(player, menu, msg.providerId);
                } else {
                    int index = (int) (-1L - msg.providerId);
                    ProviderUploadUtil.uploadFromEncodingMenuToProviderByIndex(player, menu, index);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
