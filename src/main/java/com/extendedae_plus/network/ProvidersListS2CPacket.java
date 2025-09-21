package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ui.ProviderSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: 返回可见且有空位的样板供应器列表，客户端弹窗展示供用户选择。
 */
public class ProvidersListS2CPacket implements CustomPacketPayload {
    public static final Type<ProvidersListS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "providers_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProvidersListS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.ids.size());
                for (int i = 0; i < pkt.ids.size(); i++) {
                    buf.writeLong(pkt.ids.get(i));
                    buf.writeUtf(pkt.names.get(i));
                    buf.writeVarInt(pkt.emptySlots.get(i));
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<Long> ids = new ArrayList<>(size);
                List<String> names = new ArrayList<>(size);
                List<Integer> slots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readLong());
                    names.add(buf.readUtf());
                    slots.add(buf.readVarInt());
                }
                return new ProvidersListS2CPacket(ids, names, slots);
            }
    );

    private final List<Long> ids;
    private final List<String> names;
    private final List<Integer> emptySlots;

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ProvidersListS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ProvidersListS2CPacket msg) {
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var current = mc.screen;
        mc.setScreen(new ProviderSelectScreen(current, msg.ids, msg.names, msg.emptySlots));
    }
}
