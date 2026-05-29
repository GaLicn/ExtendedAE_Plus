package com.extendedae_plus.network.crafting;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.content.ClientManualCraftingStatusStore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ManualCraftingStatusS2CPacket {
    private final int containerId;
    private final Map<AEKey, Long> manualWaiting;

    public ManualCraftingStatusS2CPacket(int containerId, Map<AEKey, Long> manualWaiting) {
        this.containerId = containerId;
        this.manualWaiting = manualWaiting;
    }

    public static void encode(ManualCraftingStatusS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.containerId);
        buf.writeVarInt(msg.manualWaiting.size());
        for (var entry : msg.manualWaiting.entrySet()) {
            AEKey.writeKey(buf, entry.getKey());
            buf.writeVarLong(entry.getValue());
        }
    }

    public static ManualCraftingStatusS2CPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readInt();
        int size = buf.readVarInt();
        Map<AEKey, Long> snapshot = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            snapshot.put(AEKey.readKey(buf), buf.readVarLong());
        }
        return new ManualCraftingStatusS2CPacket(containerId, snapshot);
    }

    public static void handle(ManualCraftingStatusS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> handleClient(msg));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ManualCraftingStatusS2CPacket msg) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.containerMenu == null) {
            ClientManualCraftingStatusStore.clear();
            return;
        }
        if (mc.player.containerMenu.containerId != msg.containerId) {
            return;
        }
        ClientManualCraftingStatusStore.setStatus(msg.containerId, msg.manualWaiting);
    }
}
