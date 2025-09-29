package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: Toggle the accelerateEnabled flag on the EntitySpeedTickerPart bound to the open menu.
 */
public class ToggleEntityTickerC2SPacket implements CustomPacketPayload  {
    public static final CustomPacketPayload.Type<ToggleEntityTickerC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "toggle_entity_ticker"));

    public static final ToggleEntityTickerC2SPacket INSTANCE = new ToggleEntityTickerC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, ToggleEntityTickerC2SPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private ToggleEntityTickerC2SPacket() {}

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ToggleEntityTickerC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

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
    }
}