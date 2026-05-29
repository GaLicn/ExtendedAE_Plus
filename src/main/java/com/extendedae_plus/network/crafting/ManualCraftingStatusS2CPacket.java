package com.extendedae_plus.network.crafting;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ClientManualCraftingStatusStore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class ManualCraftingStatusS2CPacket implements CustomPacketPayload {
    public static final Type<ManualCraftingStatusS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "manual_crafting_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManualCraftingStatusS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeInt(pkt.containerId);
                buf.writeVarInt(pkt.manualWaiting.size());
                for (var entry : pkt.manualWaiting.entrySet()) {
                    AEKey.writeKey(buf, entry.getKey());
                    buf.writeVarLong(entry.getValue());
                }
            },
            buf -> {
                int containerId = buf.readInt();
                int size = buf.readVarInt();
                Map<AEKey, Long> snapshot = new LinkedHashMap<>(size);
                for (int i = 0; i < size; i++) {
                    snapshot.put(AEKey.readKey(buf), buf.readVarLong());
                }
                return new ManualCraftingStatusS2CPacket(containerId, snapshot);
            }
    );

    private final int containerId;
    private final Map<AEKey, Long> manualWaiting;

    public ManualCraftingStatusS2CPacket(int containerId, Map<AEKey, Long> manualWaiting) {
        this.containerId = containerId;
        this.manualWaiting = manualWaiting;
    }

    public static void handle(final ManualCraftingStatusS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleClient(msg));
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
