package com.extendedae_plus.network;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: 请求当前终端可见的样板供应器列表（用于弹窗选择）。
 */
public class RequestUploadingC2SPacket implements CustomPacketPayload {
    public static final Type<RequestUploadingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "request_uploading"));

    public static final RequestUploadingC2SPacket INSTANCE = new RequestUploadingC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, RequestUploadingC2SPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public RequestUploadingC2SPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final RequestUploadingC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof PatternEncodingTermMenu encMenu)) return;

            if (encMenu.getMode() != EncodingMode.PROCESSING) {
                try {
                    ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(player, encMenu);
                } catch (Throwable ignored) {}
                return;
            }

            // 优先：若玩家也打开了样板访问终端，则用 byId 方式（精确服务器ID）
            PatternAccessTermMenu accessMenu = ExtendedAEPatternUploadUtil.getPatternAccessMenu(player);
            if (accessMenu != null) {
                List<Long> ids = ExtendedAEPatternUploadUtil.getAllProviderIds(accessMenu);
                List<Long> filteredIds = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<String> i18nKeys = new ArrayList<>();
                List<Integer> slots = new ArrayList<>();

                for (Long id : ids) {
                    if (id == null) continue;
                    if (!ExtendedAEPatternUploadUtil.isProviderAvailable(id, accessMenu)) continue;
                    int empty = ExtendedAEPatternUploadUtil.getAvailableSlots(id, accessMenu);
                    if (empty <= 0) continue; // 只列出有空位的
                    filteredIds.add(id);
                    names.add(ExtendedAEPatternUploadUtil.getProviderDisplayName(id, accessMenu));
                    i18nKeys.add(ExtendedAEPatternUploadUtil.getProviderI18nName(id, accessMenu));
                    slots.add(empty);
                }

                player.connection.send(new ProvidersListS2CPacket(filteredIds, names, i18nKeys, slots));
            } else {
                // 回退：基于编码终端所在网络枚举供应器，用“负数ID编码索引”：encodedId = -1 - index
                List<PatternContainer> containers = ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(encMenu);
                List<Long> idxIds = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<String> i18nKeys = new ArrayList<>();
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < containers.size(); i++) {
                    var c = containers.get(i);
                    if (c == null) continue;
                    int empty = ExtendedAEPatternUploadUtil.getAvailableSlots(c);
                    if (empty <= 0) continue;
                    long encodedId = -1L - i; // 约定：负数代表按索引
                    idxIds.add(encodedId);
                    names.add(ExtendedAEPatternUploadUtil.getProviderDisplayName(c));
                    i18nKeys.add(ExtendedAEPatternUploadUtil.getProviderI18nName(c));
                    slots.add(empty);
                }
                player.connection.send(new ProvidersListS2CPacket(idxIds, names, i18nKeys, slots));
            }
        });
    }
}
