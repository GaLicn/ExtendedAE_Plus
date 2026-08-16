package com.extendedae_plus.network.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;  

import java.util.function.Supplier;

/**
 * C2S：从 JEI 中键点击请求打开 AE 的下单界面。
 * 负载为一个 GenericStack（物品或流体）。
 */
public class OpenCraftFromJeiC2SPacket {
    private final GenericStack stack;

    public OpenCraftFromJeiC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    public static void encode(OpenCraftFromJeiC2SPacket msg, FriendlyByteBuf buf) {
        GenericStack.writeBuffer(msg.stack, buf);
    }

    public static OpenCraftFromJeiC2SPacket decode(FriendlyByteBuf buf) {
        var gs = GenericStack.readBuffer(buf);
        return new OpenCraftFromJeiC2SPacket(gs);
    }

    public static void handle(OpenCraftFromJeiC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || msg.stack == null) return;

            // 仅支持 AEKey 为可合成的种类
            AEKey what = msg.stack.what();

            // 定位无线终端
            var located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) return;

            // 统一调用终端 API，避免 Curios 路径绕过 WTLib 的量子桥状态。
            var grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) {
                String name = what.getDisplayName().getString();
                if (name != null && !name.isEmpty()) {
                    ModNetwork.CHANNEL.sendTo(new SetSearchTextS2CPacket(name), player.connection.connection,
                            NetworkDirection.PLAY_TO_CLIENT);
                }
                return;
            }

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, what, 1);
            }
        });
        context.setPacketHandled(true);
    }
}
