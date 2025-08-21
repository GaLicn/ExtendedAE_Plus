package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.extendedae_plus.util.WirelessTerminalLocator;
import com.extendedae_plus.util.WirelessTerminalLocator.LocatedTerminal;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
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

            IGrid grid;
            boolean usedWtHost = false;
            // Curios 情况优先通过 WTMenuHost 获取网络并由其处理能量
            String curiosSlotId = located.getCuriosSlotId();
            int curiosIndex = located.getCuriosIndex();
            WTMenuHost wtHost = null;
            if (curiosSlotId != null && curiosIndex >= 0) {
                String current = WUTHandler.getCurrentTerminal(terminal);
                WTDefinition def = WUTHandler.wirelessTerminals.get(current);
                if (def == null) return;
                wtHost = def.wTMenuHostFactory().create(player, null, terminal, (p, sub) -> {});
                if (wtHost == null) return;
                var node = wtHost.getActionableNode();
                if (node == null) return;
                grid = node.getGrid();
                if (grid == null) return;
                if (!wtHost.drainPower()) return;
                usedWtHost = true;
            } else {
                // 原生路径
                ServerLevel level = player.serverLevel();
                WirelessCraftingTerminalItem wct = terminal.getItem() instanceof WirelessCraftingTerminalItem c ? c : null;
                WirelessTerminalItem wt = wct != null ? wct : (terminal.getItem() instanceof WirelessTerminalItem t ? t : null);
                if (wt == null) return;
                grid = wt.getLinkedGrid(terminal, level, player);
                if (grid == null) return;
                if (!wt.hasPower(player, 0.5, terminal)) return;
            }

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
                if (!usedWtHost) {
                    // 扣能：与 PickFromWirelessC2SPacket 保持一致
                    WirelessCraftingTerminalItem wct2 = terminal.getItem() instanceof WirelessCraftingTerminalItem c2 ? c2 : null;
                    WirelessTerminalItem wt2 = wct2 != null ? wct2 : (terminal.getItem() instanceof WirelessTerminalItem t2 ? t2 : null);
                    if (wt2 != null) {
                        wt2.usePower(player, Math.max(0.5, extracted * 0.05), terminal);
                    }
                }
                located.commit();
                player.containerMenu.broadcastChanges();
                return;
            }

            // 无库存时：若可合成则打开下单界面
            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) return;

            if (curiosSlotId != null && curiosIndex >= 0) {
                CraftAmountMenu.open(player, new CuriosItemLocator(curiosSlotId, curiosIndex), what, 1);
            } else {
                var hand = located.getHand();
                int slot = located.getSlotIndex();
                if (hand != null) {
                    CraftAmountMenu.open(player, MenuLocators.forHand(player, hand), what, 1);
                } else if (slot >= 0) {
                    CraftAmountMenu.open(player, MenuLocators.forInventorySlot(slot), what, 1);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
