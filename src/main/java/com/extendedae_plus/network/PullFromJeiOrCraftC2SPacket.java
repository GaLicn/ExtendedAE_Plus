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
import com.extendedae_plus.util.crafting.CraftableKeyResolver;
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

            AEKey what = msg.stack.what();

            // 定位玩家持有/Curios 的无线终端
            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            // WTLib 和原生终端均由各自的 API 完成连接检查。
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            // 物品：优先从 ME 网络提取一格到背包；背包已满时维持既有行为直接放弃。
            // 非物品键（流体、Mekanism 化学品等）无法落入物品栏，跳过提取直接进入下单流程。
            if (what instanceof AEItemKey itemKey) {
                var inv = player.getInventory();
                int free = inv.getFreeSlot();
                if (free == -1) return; // 背包已满

                int targetMax = itemKey.toStack(1).getMaxStackSize();
                IEnergyService energy = grid.getEnergyService();
                MEStorage storage = grid.getStorageService().getInventory();

                AEItemKey extractedKey = itemKey;
                long extracted = StorageHelper.poweredExtraction(energy, storage, itemKey, targetMax, new PlayerSource(player));
                if (extracted <= 0) {
                    // 精确键无库存时按主键回退：JEI 条目与网络内变体可能只差数据组件。
                    extractedKey = findVariantByPrimaryKey(storage, itemKey);
                    if (extractedKey != null) {
                        extracted = StorageHelper.poweredExtraction(energy, storage, extractedKey, targetMax, new PlayerSource(player));
                    }
                }
                if (extracted > 0) {
                    inv.setItem(free, extractedKey.toStack((int) extracted));
                    WirelessTerminalLocator.useTerminalPower(player, located, Math.max(0.5, extracted * 0.05));
                    located.commit();
                    player.containerMenu.broadcastChanges();
                    return;
                }
            }

            // 无库存（或该键不可提取）时：若可合成则打开下单界面。
            // JEI 条目转出的 key 与样板输出的 key 可能只差数据组件，按主键回退解析。
            AEKey craftable = CraftableKeyResolver.resolve(grid.getCraftingService(), what);
            if (craftable == null) return;

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, craftable, 1);
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 精确键无库存时，按主键在网络中找同类变体（数据组件不同的同种物品），优先取存量最多的一格。
     */
    private static AEItemKey findVariantByPrimaryKey(MEStorage storage, AEItemKey what) {
        Object primaryKey = what.getPrimaryKey();
        AEItemKey best = null;
        long bestAmount = 0;
        for (var entry : storage.getAvailableStacks()) {
            if (!(entry.getKey() instanceof AEItemKey candidate) || candidate.equals(what)) continue;
            if (!primaryKey.equals(candidate.getPrimaryKey())) continue;
            long amount = entry.getLongValue();
            if (amount > bestAmount) {
                bestAmount = amount;
                best = candidate;
            }
        }
        return best;
    }
}
