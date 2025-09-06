package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ClientAdvancedBlockingState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：同步某个 Provider 的高级阻挡状态到客户端（本地存储）。
 */
public class AdvancedBlockingSyncS2CPacket implements CustomPacketPayload {
    public static final Type<AdvancedBlockingSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "adv_blocking_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedBlockingSyncS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.dimensionId);
                buf.writeLong(pkt.blockPosLong);
                buf.writeBoolean(pkt.enabled);
            },
            buf -> new AdvancedBlockingSyncS2CPacket(buf.readUtf(), buf.readLong(), buf.readBoolean())
    );

    private final String dimensionId;
    private final long blockPosLong;
    private final boolean enabled;

    public AdvancedBlockingSyncS2CPacket(String dimensionId, long blockPosLong, boolean enabled) {
        this.dimensionId = dimensionId;
        this.blockPosLong = blockPosLong;
        this.enabled = enabled;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final AdvancedBlockingSyncS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            String key = ClientAdvancedBlockingState.key(msg.dimensionId, msg.blockPosLong);
            ClientAdvancedBlockingState.set(key, msg.enabled);
        });
    }
}
