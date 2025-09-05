package com.extendedae_plus.network;

import com.extendedae_plus.client.ui.ProviderSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: 返回可见且有空位的样板供应器列表，客户端弹窗展示供用户选择。
 */
public class ProvidersListS2CPacket {
    private final List<Long> ids;
    private final List<String> names;
    private final List<Integer> emptySlots;

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
    }

    public static void encode(ProvidersListS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ids.size());
        for (int i = 0; i < msg.ids.size(); i++) {
            buf.writeLong(msg.ids.get(i));
            buf.writeUtf(msg.names.get(i));
            buf.writeVarInt(msg.emptySlots.get(i));
        }
    }

    public static ProvidersListS2CPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(ProvidersListS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> handleClient(msg));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ProvidersListS2CPacket msg) {
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var current = mc.screen;
        mc.setScreen(new ProviderSelectScreen(current, msg.ids, msg.names, msg.emptySlots));
    }
}
