package com.extendedae_plus.network.provider;

import com.extendedae_plus.api.IExPatternPage;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 指示客户端在已打开的样板供应器界面切换到指定页
 */
public class SetProviderPageS2CPacket {
    private final int page;

    public SetProviderPageS2CPacket(int page) {
        this.page = page;
    }

    public static void encode(SetProviderPageS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.page);
    }

    public static SetProviderPageS2CPacket decode(FriendlyByteBuf buf) {
        int p = buf.readVarInt();
        return new SetProviderPageS2CPacket(p);
    }

    public static void handle(SetProviderPageS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
                    try {
                        Screen screen = Minecraft.getInstance().screen;
                        if (screen instanceof GuiExPatternProvider guiExPatternProvider
                                && guiExPatternProvider instanceof IExPatternPage pageScreen) {
                            pageScreen.eap$setCurrentPage(msg.page);
                        }
                    } catch (Throwable ignored) {
                    }
                }
        );
        ctx.setPacketHandled(true);
    }
}
