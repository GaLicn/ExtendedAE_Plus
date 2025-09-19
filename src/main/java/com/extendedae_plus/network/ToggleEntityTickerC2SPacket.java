package com.extendedae_plus.network;

import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: Toggle the accelerateEnabled flag on the EntitySpeedTickerPart bound to the open menu.
 */
public class ToggleEntityTickerC2SPacket {
    public ToggleEntityTickerC2SPacket() {
    }

    public static void encode(ToggleEntityTickerC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static ToggleEntityTickerC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleEntityTickerC2SPacket();
    }

    public static void handle(ToggleEntityTickerC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof EntitySpeedTickerMenu menu)) return;

            EntitySpeedTickerPart part = menu.getHost();
            if (part == null) return;

            // 切换部件上的状态，并把新状态同步到菜单字段，随后广播以通知客户端
            boolean current = part.getAccelerateEnabled();
            boolean next = !current;
            part.setAccelerateEnabled(next);
            // 确保菜单上的字段也被更新，这样 @GuiSync 会把状态发回客户端
            menu.setAccelerateEnabled(next);
            menu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }
}