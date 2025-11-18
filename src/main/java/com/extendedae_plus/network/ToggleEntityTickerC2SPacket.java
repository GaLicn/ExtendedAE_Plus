package com.extendedae_plus.network;

import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: 切换 EntitySpeedTickerPart 的某个布尔配置项
 */
public class ToggleEntityTickerC2SPacket {

    private final Setting setting;

    public ToggleEntityTickerC2SPacket(Setting setting) {
        this.setting = setting;
    }

    public static void encode(ToggleEntityTickerC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.setting);
    }

    public static ToggleEntityTickerC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleEntityTickerC2SPacket(buf.readEnum(Setting.class));
    }

    public static void handle(ToggleEntityTickerC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (!(player.containerMenu instanceof EntitySpeedTickerMenu menu)) return;

            EntitySpeedTickerPart part = menu.getHost();
            if (part == null) return;

            switch (msg.setting) {
                case accelerateEnabled -> {
                    boolean newValue = !part.getAccelerateEnabled();
                    part.setAccelerateEnabled(newValue);
                    menu.setAccelerateEnabled(newValue);
                }
                case redstoneControlEnabled -> {
                    boolean newValue = !part.getRedstoneControlEnabled();
                    part.setRedstoneControlEnabled(newValue);
                    menu.setRedstoneControlEnabled(newValue);
                }
            }

            // 统一广播，让所有打开界面的客户端同步最新状态
            menu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }

    public enum Setting {
        accelerateEnabled,
        redstoneControlEnabled
    }
}