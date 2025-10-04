package com.extendedae_plus.network;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: 指示客户端对某个 AEKey 的样板进行高亮/取消高亮（仅作用于接收该包的客户端）。
 * 使用 NeoForge 1.21 Payload API。
 */
public class SetPatternHighlightS2CPacket implements CustomPacketPayload {
    public static final Type<SetPatternHighlightS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_pattern_highlight"));

    private final AEKey key;
    private final boolean highlight;

    public SetPatternHighlightS2CPacket(AEKey key, boolean highlight) {
        this.key = key;
        this.highlight = highlight;
    }

    public AEKey key() { return key; }
    public boolean highlight() { return highlight; }

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPatternHighlightS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                AEKey.writeKey(buf, pkt.key);
                buf.writeBoolean(pkt.highlight);
            },
            buf -> new SetPatternHighlightS2CPacket(AEKey.readKey(buf), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SetPatternHighlightS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                ClientPatternHighlightStore.setHighlight(msg.key, msg.highlight);
            } catch (Throwable ignored) {
            }
        });
    }
}


