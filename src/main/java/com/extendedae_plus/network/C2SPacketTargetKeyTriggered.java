package com.extendedae_plus.network;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.PatternEncodingTermMenuMixinHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SPacketTargetKeyTriggered(KeyType keyType) implements CustomPacketPayload{
    public enum KeyType {
        SHIFT_DOWN,
        SHIFT_UP
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final Type<C2SPacketTargetKeyTriggered> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "target_key_pressed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPacketTargetKeyTriggered> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(KeyType.class), C2SPacketTargetKeyTriggered::keyType,
            C2SPacketTargetKeyTriggered::new);

    public static void handle(C2SPacketTargetKeyTriggered packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) return;
            ServerPlayer player = (ServerPlayer) context.player();

            if (player.containerMenu instanceof PatternEncodingTermMenu patternMenu) {
                if (patternMenu instanceof PatternEncodingTermMenuMixinHelper accessor) {
                    accessor.eaep$setShiftPressed(switch (packet.keyType) {
                        case SHIFT_UP -> false;
                        case SHIFT_DOWN -> true;
                    });
                }
            }
        });
    }
}
