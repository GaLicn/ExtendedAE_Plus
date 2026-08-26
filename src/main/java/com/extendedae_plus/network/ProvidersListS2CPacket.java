package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.ProviderSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: 返回可见且有空位的样板供应器列表，客户端弹窗展示供用户选择。
 * <p>
 * {@link PendingSelection} 非空时表示这是批量编码队列里的一项：一键编码只在
 * 映射唯一命中时自动上传，其余的逐个下发本封包让玩家指定目标机器。
 */
public class ProvidersListS2CPacket implements CustomPacketPayload {
    public static final Type<ProvidersListS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "providers_list"));

    /**
     * 批量编码队列中当前待指定的一项。
     *
     * @param output     该样板的产物，界面上用它告诉玩家正在给哪个配方选机器
     * @param recipeId   配方 ID，客户端据此把材料图标一并画出来；解析不出来就只显示产物
     * @param index      当前项序号（从 1 开始）
     * @param total      连同当前项在内的剩余项数
     * @param searchKey  该配方类型映射出的搜索词
     * @param candidates 服务端按该搜索词匹配到的机器数（0 表示映射没命中任何机器）
     */
    public record PendingSelection(ItemStack output, ResourceLocation recipeId, int index, int total,
                                   String searchKey, int candidates) {
        /** 命中多台时用搜索词把列表预筛到候选；一台都没命中就不能预填，否则界面是空的。 */
        public String presetQuery() {
            return this.candidates > 0 && this.searchKey != null ? this.searchKey : "";
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProvidersListS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.ids.size());
                for (int i = 0; i < pkt.ids.size(); i++) {
                    buf.writeLong(pkt.ids.get(i));
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, pkt.names.get(i)); // 使用 Component 序列化
                    buf.writeVarInt(pkt.emptySlots.get(i));
                }
                buf.writeBoolean(pkt.pending != null);
                if (pkt.pending != null) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.pending.output());
                    buf.writeUtf(pkt.pending.recipeId() == null ? "" : pkt.pending.recipeId().toString(), 256);
                    buf.writeVarInt(pkt.pending.index());
                    buf.writeVarInt(pkt.pending.total());
                    buf.writeUtf(pkt.pending.searchKey() == null ? "" : pkt.pending.searchKey(), 256);
                    buf.writeVarInt(pkt.pending.candidates());
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<Long> ids = new ArrayList<>(size);
                List<Component> names = new ArrayList<>(size);
                List<Integer> slots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readLong());
                    names.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)); // 使用 Component 反序列化
                    slots.add(buf.readVarInt());
                }
                PendingSelection pending = null;
                if (buf.readBoolean()) {
                    ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ResourceLocation recipeId = ResourceLocation.tryParse(buf.readUtf(256));
                    int index = buf.readVarInt();
                    int total = buf.readVarInt();
                    String searchKey = buf.readUtf(256);
                    int candidates = buf.readVarInt();
                    pending = new PendingSelection(output, recipeId, index, total, searchKey, candidates);
                }
                return new ProvidersListS2CPacket(ids, names, slots, pending);
            }
    );

    private final List<Long> ids;
    private final List<Component> names; // 改为 Component
    private final List<Integer> emptySlots;
    private final PendingSelection pending;

    ProvidersListS2CPacket(List<Long> ids, List<Component> names, List<Integer> emptySlots) {
        this(ids, names, slotsOrEmpty(emptySlots), null);
    }

    private ProvidersListS2CPacket(List<Long> ids, List<Component> names, List<Integer> emptySlots,
                                   PendingSelection pending) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        this.pending = pending;
    }

    private static List<Integer> slotsOrEmpty(List<Integer> emptySlots) {
        return emptySlots == null ? new ArrayList<>() : emptySlots;
    }

    /** 批量编码队列专用：同一份列表附带「当前在给哪个产物选机器」。 */
    public static ProvidersListS2CPacket pendingSelection(List<Long> ids, List<Component> names,
                                                         List<Integer> emptySlots, PendingSelection pending) {
        return new ProvidersListS2CPacket(ids, names, slotsOrEmpty(emptySlots), pending);
    }

    public static void handle(final ProvidersListS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ProvidersListS2CPacket msg) {
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var current = mc.screen;
        // 队列里连着下发时，上一屏通常已经自行关闭；万一还在，取它的父屏，避免选择界面层层套娃。
        if (current instanceof ProviderSelectScreen previous && previous.isBatchPendingMode()) {
            current = previous.getParentScreen();
        }
        mc.setScreen(msg.pending == null
                ? new ProviderSelectScreen(current, msg.ids, msg.names, msg.emptySlots)
                : new ProviderSelectScreen(current, msg.ids, msg.names, msg.emptySlots, msg.pending));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
