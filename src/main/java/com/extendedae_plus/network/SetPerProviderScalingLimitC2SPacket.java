package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.advancedae.accessor.AdvPatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAccessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

/**
 * C2S: set per-provider scaling limit for the currently opened provider and pattern index
 */
public class SetPerProviderScalingLimitC2SPacket implements CustomPacketPayload {
    private final int limit;

    public static final CustomPacketPayload.Type<SetPerProviderScalingLimitC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_per_provider_scaling_limit"));


    public SetPerProviderScalingLimitC2SPacket(int limit) {
        this.limit = limit;
    }
    public int limit() { return limit; }

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPerProviderScalingLimitC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeInt(pkt.limit);
            },
            buf -> new SetPerProviderScalingLimitC2SPacket(buf.readInt())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SetPerProviderScalingLimitC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (!(ctx.player() instanceof ServerPlayer player)) {
                    return;
                }
                var containerMenu = player.containerMenu;
                if (containerMenu instanceof PatternProviderMenu menu) {
                    var accessor = (PatternProviderMenuAccessor) menu;
                    var logic = accessor.eap$logic();
                    if (logic instanceof ISmartDoublingHolder handler) {
                        handler.eap$setProviderSmartDoublingLimit(msg.limit);
                        logic.saveChanges();
                    }
                } else if (containerMenu instanceof AdvPatternProviderMenu advMenu) {
                    var accessor = (AdvPatternProviderMenuAdvancedAccessor) advMenu;
                    var logic = accessor.eap$logic();
                    if (logic instanceof ISmartDoublingHolder handler) {
                        handler.eap$setProviderSmartDoublingLimit(msg.limit);
                        logic.saveChanges();
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }
}


