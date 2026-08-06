package com.extendedae_plus.network.jei;

import appeng.api.stacks.AEKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncNetworkInventoryS2CPacket {

    private final boolean fullUpdate;
    private final List<Entry> entries;

    public SyncNetworkInventoryS2CPacket(boolean fullUpdate, List<Entry> entries) {
        this.fullUpdate = fullUpdate;
        this.entries = entries;
    }

    public static void encode(SyncNetworkInventoryS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.fullUpdate);
        buf.writeVarInt(msg.entries.size());
        for (var entry : msg.entries) {
            buf.writeVarLong(entry.serial);
            boolean hasKey = entry.what != null;
            buf.writeBoolean(hasKey);
            if (hasKey) {
                AEKey.writeOptionalKey(buf, entry.what);
            }
            buf.writeVarLong(entry.amount);
            buf.writeBoolean(entry.craftable);
        }
    }

    public static SyncNetworkInventoryS2CPacket decode(FriendlyByteBuf buf) {
        boolean fullUpdate = buf.readBoolean();
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long serial = buf.readVarLong();
            AEKey what = null;
            if (buf.readBoolean()) {
                what = AEKey.readOptionalKey(buf);
            }
            long amount = buf.readVarLong();
            boolean craftable = buf.readBoolean();
            entries.add(new Entry(serial, what, amount, craftable));
        }
        return new SyncNetworkInventoryS2CPacket(fullUpdate, entries);
    }

    public static void handle(SyncNetworkInventoryS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.extendedae_plus.client.jei.NetworkItemCache.INSTANCE.handleUpdate(msg.fullUpdate, msg.entries);
        });
        ctx.get().setPacketHandled(true);
    }

    public boolean isFullUpdate() {
        return fullUpdate;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public static class Entry {
        public final long serial;
        public final AEKey what;
        public final long amount;
        public final boolean craftable;

        public Entry(long serial, AEKey what, long amount, boolean craftable) {
            this.serial = serial;
            this.what = what;
            this.amount = amount;
            this.craftable = craftable;
        }
    }
}
