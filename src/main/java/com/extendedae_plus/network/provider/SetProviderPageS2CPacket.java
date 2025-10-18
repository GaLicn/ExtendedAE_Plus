package com.extendedae_plus.network.provider;

import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
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
                        if (screen instanceof GuiExPatternProvider guiExPatternProvider) {
                            Field currentPage = screen.getClass().getDeclaredField("eap$currentPage");
                            currentPage.setAccessible(true);
                            currentPage.setInt(guiExPatternProvider, msg.page);


                            guiExPatternProvider.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                            guiExPatternProvider.repositionSlots(SlotSemantics.STORAGE);

                            Field hs = screen.getClass().getDeclaredField("hoveredSlot");
                            hs.setAccessible(true);
                            hs.set(screen, null);
                        }
                    } catch (Throwable ignored) {
                    }
                }
        );
        ctx.setPacketHandled(true);
    }
}


