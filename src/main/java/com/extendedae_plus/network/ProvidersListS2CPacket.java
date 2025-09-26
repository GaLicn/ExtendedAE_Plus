package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ui.ProviderSelectScreen;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S2C: 返回可见且有空位的样板供应器列表，客户端弹窗展示供用户选择。
 */
public record ProvidersListS2CPacket(List<Long> ids, List<String> names, List<String> i18nKeys,
                                     List<Integer> emptySlots) implements CustomPacketPayload {
    public static final Type<ProvidersListS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "providers_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProvidersListS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Codec.list(Codec.LONG)), ProvidersListS2CPacket::ids,
            ByteBufCodecs.fromCodec(Codec.list(Codec.STRING)), ProvidersListS2CPacket::names,
            ByteBufCodecs.fromCodec(Codec.list(Codec.STRING)), ProvidersListS2CPacket::i18nKeys,
            ByteBufCodecs.fromCodec(Codec.list(Codec.INT)), ProvidersListS2CPacket::emptySlots,
            ProvidersListS2CPacket::new
    );

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
        var current = mc.screen;
        mc.setScreen(new ProviderSelectScreen(current, msg.ids, msg.names, msg.i18nKeys, msg.emptySlots));
    }
}
