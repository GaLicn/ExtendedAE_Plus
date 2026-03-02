package com.extendedae_plus.mixin.ae2.network;

import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PatternAccessTerminalPacket.class)
public abstract class PatternAccessTerminalPacketMixin {

    private static final int EAP_MAX_SYNCED_SLOTS = 4096;

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/ByteBufCodecs;map(Ljava/util/function/IntFunction;Lnet/minecraft/network/codec/StreamCodec;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/codec/StreamCodec;"
            ),
            index = 3
    )
    private static int eap$expandPatternAccessSlotsLimit(int originalLimit) {
        return Math.max(originalLimit, EAP_MAX_SYNCED_SLOTS);
    }
}
