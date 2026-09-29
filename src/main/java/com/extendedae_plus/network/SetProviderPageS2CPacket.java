package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: 指示客户端在已打开的样板供应器界面切换到指定页
 */
public class SetProviderPageS2CPacket implements CustomPacketPayload {
    public static final Type<SetProviderPageS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_provider_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProviderPageS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeVarInt(pkt.page),
            buf -> new SetProviderPageS2CPacket(buf.readVarInt())
    );

    private final int page;

    public SetProviderPageS2CPacket(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}


