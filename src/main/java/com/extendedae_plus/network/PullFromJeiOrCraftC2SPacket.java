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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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

    public static void handle(final PullFromJeiOrCraftC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (msg.stack == null) return;

            AEKey what = msg.stack.what();
            if (!(what instanceof AEItemKey itemKey)) return;

            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            // WTLib 终端由其菜单主机校验无线接入点与量子桥状态。
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            var inv = player.getInventory();
            int free = inv.getFreeSlot();
            if (free == -1) return;

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

            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) return;

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, what, 1);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
