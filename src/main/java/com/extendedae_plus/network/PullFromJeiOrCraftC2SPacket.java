package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator.LocatedTerminal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PullFromJeiOrCraftC2SPacket {
    private final GenericStack stack;

    public PullFromJeiOrCraftC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    public static void encode(PullFromJeiOrCraftC2SPacket msg, FriendlyByteBuf buf) {
        GenericStack.writeBuffer(msg.stack, buf);
    }

    public static PullFromJeiOrCraftC2SPacket decode(FriendlyByteBuf buf) {
        var gs = GenericStack.readBuffer(buf);
        return new PullFromJeiOrCraftC2SPacket(gs);
    }

    public static void handle(PullFromJeiOrCraftC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || msg.stack == null) return;

            // 仅处理物品
            AEKey what = msg.stack.what();
            if (!(what instanceof AEItemKey itemKey)) return;

            // 定位玩家持有/Curios 的无线终端
            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            // WTLib 和原生终端均由各自的 API 完成连接检查。
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            // 仅放入背包空槽位
            var inv = player.getInventory();
            int free = inv.getFreeSlot();
            if (free == -1) return; // 背包已满

            int targetMax = itemKey.toStack(1).getMaxStackSize();
            IEnergyService energy = grid.getEnergyService();
            MEStorage storage = grid.getStorageService().getInventory();

            long extracted = StorageHelper.poweredExtraction(energy, storage, itemKey, targetMax, new PlayerSource(player));
            if (extracted > 0) {
                inv.setItem(free, itemKey.toStack((int) extracted));
                WirelessTerminalLocator.useTerminalPower(player, located, Math.max(0.5, extracted * 0.05));
                located.commit();
                player.containerMenu.broadcastChanges();
                return;
            }

            // 无库存时：若可合成则打开下单界面
            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) return;

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, what, 1);
            }
        });
        context.setPacketHandled(true);
    }
}
