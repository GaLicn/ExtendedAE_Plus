package com.extendedae_plus.network.pattern;

import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CancelPendingPatternC2SPacket {

    public static final CancelPendingPatternC2SPacket INSTANCE = new CancelPendingPatternC2SPacket();

    public static void encode(CancelPendingPatternC2SPacket msg, FriendlyByteBuf buf){}

    public static CancelPendingPatternC2SPacket decode(FriendlyByteBuf buf) {return INSTANCE;}

    public static void handle(CancelPendingPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier){
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(()->{
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (ProviderUploadUtil.hasPendingCtrlQPattern(player)){
                ProviderUploadUtil.returnPendingCtrlQPatternToInventory(player);
            }

        });
    }
}
