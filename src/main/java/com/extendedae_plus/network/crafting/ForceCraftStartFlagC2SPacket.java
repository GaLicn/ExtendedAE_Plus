package com.extendedae_plus.network.crafting;

import appeng.menu.me.crafting.CraftConfirmMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.crafting.IForceCraftStartSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ForceCraftStartFlagC2SPacket implements CustomPacketPayload {
    public static final Type<ForceCraftStartFlagC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "force_craft_start_flag"));

    public static final StreamCodec<FriendlyByteBuf, ForceCraftStartFlagC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBoolean(pkt.forceStart),
            buf -> new ForceCraftStartFlagC2SPacket(buf.readBoolean())
    );

    private final boolean forceStart;

    public ForceCraftStartFlagC2SPacket(boolean forceStart) {
        this.forceStart = forceStart;
    }

    public boolean forceStart() {
        return this.forceStart;
    }

    public static void handle(final ForceCraftStartFlagC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof CraftConfirmMenu menu
                    && menu instanceof IForceCraftStartSync sync) {
                sync.eap$clientSetForceCraftStart(msg.forceStart());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
