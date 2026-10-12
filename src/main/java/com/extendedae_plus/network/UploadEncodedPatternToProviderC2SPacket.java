package com.extendedae_plus.network;

import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.UploadOutcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: Request uploading an encoded pattern to a selected provider.
 *
 * <p>目标供应器已含「除编码作者以外完全相同」的样板时拦截：不上传、回收样板并提示。</p>
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

            // 优先处理 Ctrl+Q pending 上传
            if (ProviderUploadUtil.hasPendingCtrlQPattern(player)) {
                UploadOutcome outcome = ProviderUploadUtil.uploadPendingCtrlQPattern(player, msg.providerId);
                if (outcome != UploadOutcome.SUCCESS) {
                    // 重复与失败都退回背包：样板不能凭空消失
                    ProviderUploadUtil.returnPendingCtrlQPatternToInventory(player);
                }
                sendStatus(player, msg, outcome);
                return;
            }

            UploadOutcome outcome = UploadOutcome.FAILED;
            if (player.containerMenu instanceof IPatternUploadMenu menu) {
                // 支持两种模式：
                // 1) providerId >= 0: 访问终端 byId 模式
                // 2) providerId < 0:   索引模式（由列表回退路径生成），index = -1 - providerId
                if (msg.providerId >= 0) {
                    outcome = ProviderUploadUtil.uploadFromEncodingMenuToProvider(player, menu, msg.providerId);
                } else {
                    int index = (int) (-1L - msg.providerId);
                    outcome = ProviderUploadUtil.uploadFromEncodingMenuToProviderByIndex(player, menu, index);
                }
                if (outcome == UploadOutcome.DUPLICATE) {
                    // 重复命中：编码槽样板未写入，回收为等量空白样板
                    var slot = menu.getEncodedPatternSlot();
                    if (slot != null) {
                        ProviderUploadUtil.recycleEncodedPatternAsBlanks(player, menu, slot.getItem());
                    }
                }
            }

            sendStatus(player, msg, outcome);
        });
        ctx.setPacketHandled(true);
    }

    /**
     * 回传结果提示。「重复」是用户必须知道的结果，始终发送；成功/失败仍受 {@code showStatusMessage} 约束。
     */
    private static void sendStatus(ServerPlayer player, UploadEncodedPatternToProviderC2SPacket msg, UploadOutcome outcome) {
        if (player == null) {
            return;
        }
        String providerDisplayName = msg.providerName == null || msg.providerName.isBlank()
                ? "#" + msg.providerId
                : msg.providerName;
        if (outcome == UploadOutcome.DUPLICATE) {
            player.sendSystemMessage(Component.translatable(
                    "extendedae_plus.message.provider.duplicate", providerDisplayName));
            return;
        }
        if (!msg.showStatusMessage) {
            return;
        }
        player.sendSystemMessage(Component.translatable(
                outcome == UploadOutcome.SUCCESS
                        ? "extendedae_plus.screen.upload.auto_upload_success"
                        : "extendedae_plus.screen.upload.auto_upload_failed",
                providerDisplayName
        ));
    }
}