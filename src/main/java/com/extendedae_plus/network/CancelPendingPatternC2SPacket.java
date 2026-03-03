package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class CancelPendingPatternC2SPacket implements CustomPacketPayload {

    public static final Type<CancelPendingPatternC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID,"cancel_pending_pattern"));

    public static final CancelPendingPatternC2SPacket INSTANCE = new CancelPendingPatternC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, CancelPendingPatternC2SPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(final CancelPendingPatternC2SPacket msg, final IPayloadContext ctx){
        ctx.enqueueWork(() ->{
            if(!((ctx.player()) instanceof ServerPlayer player)) return;

            if(CtrlQPendingUploadUtil.hasPendingCtrlQPattern(player)){
                CtrlQPendingUploadUtil.returnPendingCtrlQPatternToInventory(player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
