package com.extendedae_plus.network.crafting;

import appeng.menu.me.crafting.CraftConfirmMenu;
import com.extendedae_plus.api.crafting.IForceCraftStartSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ForceCraftStartFlagC2SPacket(boolean forceStart) {

    public static void encode(ForceCraftStartFlagC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.forceStart());
    }

    public static ForceCraftStartFlagC2SPacket decode(FriendlyByteBuf buf) {
        return new ForceCraftStartFlagC2SPacket(buf.readBoolean());
    }

    public static void handle(ForceCraftStartFlagC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof CraftConfirmMenu menu
                    && menu instanceof IForceCraftStartSync sync) {
                sync.eap$clientSetForceCraftStart(msg.forceStart());
            }
        });
        ctx.setPacketHandled(true);
    }
}
