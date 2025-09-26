package com.extendedae_plus.network.provider;

import com.extendedae_plus.client.ClientAdvancedBlockingState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdvancedBlockingSyncS2CPacket {
    private final String dimensionId;
    private final long blockPosLong;
    private final boolean enabled;

    public AdvancedBlockingSyncS2CPacket(String dimensionId, long blockPosLong, boolean enabled) {
        this.dimensionId = dimensionId;
        this.blockPosLong = blockPosLong;
        this.enabled = enabled;
    }

    public static void encode(AdvancedBlockingSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dimensionId);
        buf.writeLong(msg.blockPosLong);
        buf.writeBoolean(msg.enabled);
    }

    public static AdvancedBlockingSyncS2CPacket decode(FriendlyByteBuf buf) {
        String dim = buf.readUtf();
        long pos = buf.readLong();
        boolean en = buf.readBoolean();
        return new AdvancedBlockingSyncS2CPacket(dim, pos, en);
    }

    public static void handle(AdvancedBlockingSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            String key = ClientAdvancedBlockingState.key(msg.dimensionId, msg.blockPosLong);
            ClientAdvancedBlockingState.set(key, msg.enabled);
        });
        ctx.setPacketHandled(true);
    }
}
