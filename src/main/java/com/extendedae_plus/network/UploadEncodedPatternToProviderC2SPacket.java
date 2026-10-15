package com.extendedae_plus.network;

import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.UploadOutcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 请求将图样编码终端的已编码样板上传到指定的样板供应器（由客户端选择）。
 *
 * <p>目标供应器已含「除编码作者以外完全相同」的样板时拦截：不上传、回收样板并提示。</p>
 */
public class UploadEncodedPatternToProviderC2SPacket implements CustomPacketPayload {
    public static final Type<UploadEncodedPatternToProviderC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "upload_pattern_to_provider"));

    public static final StreamCodec<FriendlyByteBuf, UploadEncodedPatternToProviderC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeLong(pkt.providerId);
                buf.writeBoolean(pkt.showStatusMessage);
                buf.writeUtf(pkt.providerName, 256);
            },
            buf -> new UploadEncodedPatternToProviderC2SPacket(
                    buf.readLong(), buf.readBoolean(), buf.readUtf(256))
    );
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

    public static void handle(final UploadEncodedPatternToProviderC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 优先处理 Ctrl+Q pending 上传
            if (CtrlQPendingUploadUtil.hasPendingCtrlQPattern(player)) {
                UploadOutcome outcome = CtrlQPendingUploadUtil.uploadPendingCtrlQPattern(
                        player, msg.providerId);
                if (outcome != UploadOutcome.SUCCESS) {
                    // 重复与失败都退回背包：样板不能凭空消失
                    CtrlQPendingUploadUtil.returnPendingCtrlQPatternToInventory(player);
                }
                sendStatus(player, msg, outcome);
                return;
            }

            IPatternUploadMenu menu = player.containerMenu instanceof IPatternUploadMenu m ? m : null;
            UploadOutcome outcome = UploadOutcome.FAILED;
            if (menu != null) {
                // 支持两种模式：
                // 1) providerId >= 0: 访问终端 byId 模式
                // 2) providerId < 0:   索引模式（由列表回退路径生成），index = -1 - providerId
                if (msg.providerId >= 0) {
                    outcome = ProviderUploadUtil.uploadFromEncodingMenuToProvider(
                            player, menu, msg.providerId);
                } else {
                    int index = (int) (-1L - msg.providerId);
                    outcome = ProviderUploadUtil.uploadFromEncodingMenuToProviderByIndex(
                            player, menu, index);
                }
            }
            if (outcome == UploadOutcome.DUPLICATE && menu != null) {
                // 重复命中：编码槽样板未写入，回收为等量空白样板
                var slot = menu.getEncodedPatternSlot();
                if (slot != null) {
                    ProviderUploadUtil.recycleEncodedPatternAsBlanks(player, menu, slot.getItem());
                }
            }
            sendStatus(player, msg, outcome);
        });
    }

    /**
     * 回传结果提示。「重复」是用户必须知道的结果，始终发送；成功/失败仍受 {@code showStatusMessage} 约束。
     */
    private static void sendStatus(ServerPlayer player, UploadEncodedPatternToProviderC2SPacket msg, UploadOutcome outcome) {
        if (player == null) {
            return;
        }
        String providerDisplayName = msg.providerName.isBlank() ? "#" + msg.providerId : msg.providerName;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}