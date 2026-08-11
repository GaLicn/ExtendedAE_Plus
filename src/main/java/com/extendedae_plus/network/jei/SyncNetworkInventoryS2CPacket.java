package com.extendedae_plus.network.jei;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.jei.NetworkItemCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncNetworkInventoryS2CPacket(boolean fullUpdate, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SyncNetworkInventoryS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "sync_network_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNetworkInventoryS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.fullUpdate);
                buf.writeVarInt(packet.entries.size());
                for (Entry entry : packet.entries) {
                    buf.writeVarLong(entry.serial);
                    buf.writeBoolean(entry.key != null);
                    if (entry.key != null) {
                        AEKey.writeKey(buf, entry.key);
                    }
                    buf.writeVarLong(entry.amount);
                    buf.writeBoolean(entry.craftable);
                }
            },
            buf -> {
                boolean fullUpdate = buf.readBoolean();
                int size = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    long serial = buf.readVarLong();
                    AEKey key = buf.readBoolean() ? AEKey.readKey(buf) : null;
                    entries.add(new Entry(serial, key, buf.readVarLong(), buf.readBoolean()));
                }
                return new SyncNetworkInventoryS2CPacket(fullUpdate, entries);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncNetworkInventoryS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> NetworkItemCache.INSTANCE.handleUpdate(packet.fullUpdate, packet.entries));
    }

    public record Entry(long serial, AEKey key, long amount, boolean craftable) {
    }
}
