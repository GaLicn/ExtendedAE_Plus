package com.extendedae_plus.network;

import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: Request uploading an encoded pattern to a selected provider.
 */
public class UploadEncodedPatternToProviderC2SPacket {
    private final long providerId;
    private final boolean showStatusMessage;
    private final String providerName;

    public UploadEncodedPatternToProviderC2SPacket(long providerId) {
        this(providerId, false, "");
    }

    public UploadEncodedPatternToProviderC2SPacket(long providerId, boolean showStatusMessage, String providerName) {
        this.providerId = providerId;
        this.showStatusMessage = showStatusMessage;
        this.providerName = providerName == null ? "" : providerName;
    }

    public static void encode(UploadEncodedPatternToProviderC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.providerId);
        buf.writeBoolean(msg.showStatusMessage);
        buf.writeUtf(msg.providerName, 256);
    }

    public static UploadEncodedPatternToProviderC2SPacket decode(FriendlyByteBuf buf) {
        return new UploadEncodedPatternToProviderC2SPacket(buf.readLong(), buf.readBoolean(), buf.readUtf(256));
    }

    public static void handle(UploadEncodedPatternToProviderC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            boolean uploaded = false;

            if (ProviderUploadUtil.hasPendingCtrlQPattern(player)) {
                uploaded = ProviderUploadUtil.uploadPendingCtrlQPattern(player, msg.providerId);
                if (!uploaded) {
                    ProviderUploadUtil.returnPendingCtrlQPatternToInventory(player);
                }
                sendAutoUploadStatus(player, msg, uploaded);
                return;
            }

            if (player.containerMenu instanceof IPatternUploadMenu menu) {
                if (msg.providerId >= 0) {
                    uploaded = ProviderUploadUtil.uploadFromEncodingMenuToProvider(player, menu, msg.providerId);
                } else {
                    int index = (int) (-1L - msg.providerId);
                    uploaded = ProviderUploadUtil.uploadFromEncodingMenuToProviderByIndex(player, menu, index);
                }
            }

            sendAutoUploadStatus(player, msg, uploaded);
        });
        ctx.setPacketHandled(true);
    }

    private static void sendAutoUploadStatus(ServerPlayer player, UploadEncodedPatternToProviderC2SPacket msg, boolean uploaded) {
        if (player == null || !msg.showStatusMessage) {
            return;
        }
        String providerDisplayName = msg.providerName == null || msg.providerName.isBlank()
                ? "#" + msg.providerId
                : msg.providerName;
        player.sendSystemMessage(Component.translatable(
                uploaded
                        ? "extendedae_plus.screen.upload.auto_upload_success"
                        : "extendedae_plus.screen.upload.auto_upload_failed",
                providerDisplayName
        ));
    }
}
