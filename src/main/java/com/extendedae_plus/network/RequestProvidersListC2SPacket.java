package com.extendedae_plus.network;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
public class RequestProvidersListC2SPacket implements CustomPacketPayload {
    public static final Type<RequestProvidersListC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "request_providers_list"));

    public static final RequestProvidersListC2SPacket INSTANCE = new RequestProvidersListC2SPacket(false);
    /** 静默模式：只回填客户端缓存用于本地唯一匹配判定，不打开弹窗。 */
    public static final RequestProvidersListC2SPacket SILENT = new RequestProvidersListC2SPacket(true);

    public static final StreamCodec<FriendlyByteBuf, RequestProvidersListC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBoolean(pkt.silent),
            buf -> new RequestProvidersListC2SPacket(buf.readBoolean())
    );

    private final boolean silent;

    private RequestProvidersListC2SPacket(boolean silent) {
        this.silent = silent;
    }

    public static void handle(final RequestProvidersListC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Ctrl+Q pending 模式：不依赖编码终端，直接基于玩家网络给出列表（负数索引 ID）
            if (CtrlQPendingUploadUtil.hasPendingCtrlQPattern(player)) {
                List<PatternContainer> containers = CtrlQPendingUploadUtil.listAvailableProvidersFromPlayerNetwork(player);
                List<Long> idxIds = new ArrayList<>();
                List<Component> names = new ArrayList<>();
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < containers.size(); i++) {
                    var c = containers.get(i);
                    if (c == null) continue;
                    int empty = ProviderUploadUtil.getAvailableSlots(c);
                    // 槽满但还能装扩容卡的也列出：上传时会自动从网络取卡扩容
                    if (empty <= 0 && !ProviderUploadUtil.canInstallExpansionCard(c)) continue;
                    long encodedId = -1L - i;
                    idxIds.add(encodedId);
                    names.add(ProviderUploadUtil.getProviderDisplayNameComponent(c));
                    slots.add(empty);
                }
                player.connection.send(new ProvidersListS2CPacket(idxIds, names, slots, msg.silent));
                return;
            }

            if (!(player.containerMenu instanceof IPatternUploadMenu encMenu)) return;

            // 优先：若玩家也打开了样板访问终端，则用 byId 方式（精确服务器ID）
            PatternAccessTermMenu accessMenu = ProviderUploadUtil.getPatternAccessMenu(player);
            if (accessMenu != null) {
                List<Long> ids = ProviderUploadUtil.getAllProviderIds(accessMenu);
                List<Long> filteredIds = new ArrayList<>();
                List<Component> names = new ArrayList<>();
                List<Integer> slots = new ArrayList<>();

                for (Long id : ids) {
                    if (id == null) continue;
                    if (!ProviderUploadUtil.isProviderAvailable(id, accessMenu)) continue;
                    int empty = ProviderUploadUtil.getAvailableSlots(id, accessMenu);
                    // 槽满但还能装扩容卡的也列出：上传时会自动从网络取卡扩容
                    if (empty <= 0 && !ProviderUploadUtil.canInstallExpansionCard(id, accessMenu)) continue;
                    filteredIds.add(id);
                    names.add(ProviderUploadUtil.getProviderDisplayNameComponent(id, accessMenu));
                    slots.add(empty);
                }

                player.connection.send(new ProvidersListS2CPacket(filteredIds, names, slots, msg.silent));
                return;
            }

            // 回退：基于编码终端所在网络枚举供应器，用“负数ID编码索引”：encodedId = -1 - index
            List<PatternContainer> containers = ProviderUploadUtil.listAvailableProvidersFromGrid(encMenu);
            List<Long> idxIds = new ArrayList<>();
            List<Component> names = new ArrayList<>();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < containers.size(); i++) {
                var c = containers.get(i);
                if (c == null) continue;
                int empty = ProviderUploadUtil.getAvailableSlots(c);
                // 槽满但还能装扩容卡的也列出：上传时会自动从网络取卡扩容
                if (empty <= 0 && !ProviderUploadUtil.canInstallExpansionCard(c)) continue;
                long encodedId = -1L - i; // 约定：负数代表按索引
                idxIds.add(encodedId);
                names.add(ProviderUploadUtil.getProviderDisplayNameComponent(c));
                slots.add(empty);
            }
            player.connection.send(new ProvidersListS2CPacket(idxIds, names, slots, msg.silent));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
