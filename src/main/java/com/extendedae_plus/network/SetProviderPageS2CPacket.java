package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: 指示客户端在已打开的样板供应器界面切换到指定页
 */
public class SetProviderPageS2CPacket implements CustomPacketPayload {
    public static final Type<SetProviderPageS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_provider_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProviderPageS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeVarInt(pkt.page),
            buf -> new SetProviderPageS2CPacket(buf.readVarInt())
    );

    private final int page;

    public SetProviderPageS2CPacket(int page) {
        this.page = page;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SetProviderPageS2CPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Screen screen = Minecraft.getInstance().screen;
                if (screen instanceof GuiExPatternProvider guiExPatternProvider
                        && guiExPatternProvider instanceof IExPatternPage pageAccessor) {
                    pageAccessor.eap$setCurrentPage(msg.page);
                    ((AbstractContainerScreenAccessor<?>) (Object) guiExPatternProvider).eap$setHoveredSlot(null);
                }
            } catch (Throwable ignored) {
            }
        });
    }
}


