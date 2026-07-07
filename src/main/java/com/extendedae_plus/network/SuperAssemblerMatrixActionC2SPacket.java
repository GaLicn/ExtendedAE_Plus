package com.extendedae_plus.network;

import com.extendedae_plus.menu.SuperAssemblerMatrixMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperAssemblerMatrixActionC2SPacket {

    private final String action;

    public SuperAssemblerMatrixActionC2SPacket(String action) {
        this.action = action;
    }

    public static void encode(SuperAssemblerMatrixActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.action, 64);
    }

    public static SuperAssemblerMatrixActionC2SPacket decode(FriendlyByteBuf buf) {
        return new SuperAssemblerMatrixActionC2SPacket(buf.readUtf(64));
    }

    public static void handle(SuperAssemblerMatrixActionC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.containerMenu instanceof SuperAssemblerMatrixMenu menu) {
                menu.handleAction(packet.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
