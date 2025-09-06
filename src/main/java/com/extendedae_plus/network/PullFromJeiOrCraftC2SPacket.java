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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PullFromJeiOrCraftC2SPacket implements CustomPacketPayload {
    public static final Type<PullFromJeiOrCraftC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "pull_from_jei_or_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullFromJeiOrCraftC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> GenericStack.writeBuffer(pkt.stack, buf),
            buf -> new PullFromJeiOrCraftC2SPacket(GenericStack.readBuffer(buf))
    );

    private final GenericStack stack;

    public PullFromJeiOrCraftC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PullFromJeiOrCraftC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (msg.stack == null) return;

            AEKey what = msg.stack.what();
            if (!(what instanceof AEItemKey itemKey)) return;

            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            IGrid grid;
            // 统一 AE2 原生路径
            ServerLevel level = player.serverLevel();
            WirelessCraftingTerminalItem wct = terminal.getItem() instanceof WirelessCraftingTerminalItem c ? c : null;
            WirelessTerminalItem wt = wct != null ? wct : (terminal.getItem() instanceof WirelessTerminalItem t ? t : null);
            if (wt == null) return;
            grid = wt.getLinkedGrid(terminal, level, null);
            if (grid == null) return;
            if (!wt.hasPower(player, 0.5, terminal)) return;

            var inv = player.getInventory();
            int free = inv.getFreeSlot();
            if (free == -1) return;

            int targetMax = itemKey.toStack(1).getMaxStackSize();
            IEnergyService energy = grid.getEnergyService();
            MEStorage storage = grid.getStorageService().getInventory();

            long extracted = StorageHelper.poweredExtraction(energy, storage, itemKey, targetMax, new PlayerSource(player));
            if (extracted > 0) {
                inv.setItem(free, itemKey.toStack((int) extracted));
                WirelessCraftingTerminalItem wct2 = terminal.getItem() instanceof WirelessCraftingTerminalItem c2 ? c2 : null;
                WirelessTerminalItem wt2 = wct2 != null ? wct2 : (terminal.getItem() instanceof WirelessTerminalItem t2 ? t2 : null);
                if (wt2 != null) {
                    wt2.usePower(player, Math.max(0.5, extracted * 0.05), terminal);
                }
                located.commit();
                player.containerMenu.broadcastChanges();
                return;
            }

            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) return;

            String curiosSlotId = located.getCuriosSlotId();
            int curiosIndex = located.getCuriosIndex();
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
    }
}
