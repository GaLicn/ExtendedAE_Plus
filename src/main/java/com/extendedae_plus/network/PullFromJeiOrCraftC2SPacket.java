package com.extendedae_plus.network;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.util.fluid.FluidSoundHelper;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.crafting.CraftableKeyResolver;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator.LocatedTerminal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PullFromJeiOrCraftC2SPacket implements CustomPacketPayload {
    public static final Type<PullFromJeiOrCraftC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "pull_from_jei_or_craft"));

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

            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            // WTLib 终端由其菜单主机校验无线接入点与量子桥状态。
            IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            if (what instanceof AEFluidKey fluidKey && fluidKey.getFluid().getBucket() != Items.AIR) {
                pullFluidBucket(player, located, grid, fluidKey);
                return;
            }

            // 物品：优先从 ME 网络提取一格到背包；背包已满时维持既有行为直接放弃。
            if (what instanceof AEItemKey itemKey) {
                var inv = player.getInventory();
                int free = inv.getFreeSlot();
                if (free == -1) return;

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
                    finishPull(player, located, extracted);
                    return;
                }
            }

            openCrafting(player, located, grid, what);
        });
    }

    private static void pullFluidBucket(ServerPlayer player, LocatedTerminal located, IGrid grid, AEFluidKey fluid) {
        int bucketAmount = AEFluidKey.AMOUNT_BUCKET;
        ItemStack filledBucket = FluidUtil.getFilledBucket(fluid.toStack(bucketAmount));
        if (filledBucket.isEmpty()) {
            openCrafting(player, located, grid, fluid);
            return;
        }

        var inv = player.getInventory();
        if (inv.getFreeSlot() == -1 && inv.getSlotWithRemainingSpace(filledBucket) == -1) return;

        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        var source = new PlayerSource(player);
        if (storage.extract(fluid, bucketAmount, Actionable.SIMULATE, source) < bucketAmount) {
            openCrafting(player, located, grid, fluid);
            return;
        }

        var emptyBucket = AEItemKey.of(Items.BUCKET);
        if (storage.extract(emptyBucket, 1, Actionable.SIMULATE, source) == 0) {
            // 下单与提取使用相同的空桶键，确保样板产出的桶可用。
            if (grid.getCraftingService().isCraftable(emptyBucket)) {
                openCraftAmount(player, located, emptyBucket);
            }
            return;
        }

        // 和 AE2 终端一样消耗 ME 中的空桶，并用带电提取装满一桶流体。
        if (StorageHelper.poweredExtraction(energy, storage, fluid, bucketAmount, source, Actionable.SIMULATE) < bucketAmount) {
            return;
        }
        if (storage.extract(emptyBucket, 1, Actionable.MODULATE, source) == 0) return;

        long extracted = StorageHelper.poweredExtraction(energy, storage, fluid, bucketAmount, source);
        if (extracted < bucketAmount) {
            // 实际提取与模拟不一致时退回流体和空桶，避免发放未装满的桶。
            if (extracted > 0) {
                long returned = storage.insert(fluid, extracted, Actionable.MODULATE, source);
                if (returned < extracted) {
                    ExtendedAEPlus.LOGGER.warn("Could not return {} mB of {} after filling a bucket failed",
                            extracted - returned, fluid);
                }
            }
            if (storage.insert(emptyBucket, 1, Actionable.MODULATE, source) == 0) {
                inv.placeItemBackInInventory(emptyBucket.toStack(1));
            }
            return;
        }

        inv.placeItemBackInInventory(filledBucket);
        FluidSoundHelper.playFillSound(player, fluid);
        finishPull(player, located, 1);
    }

    private static void finishPull(ServerPlayer player, LocatedTerminal located, long itemCount) {
        WirelessTerminalLocator.useTerminalPower(player, located, Math.max(0.5, itemCount * 0.05));
        located.commit();
        player.containerMenu.broadcastChanges();
    }

    private static void openCrafting(ServerPlayer player, LocatedTerminal located, IGrid grid, AEKey what) {
        AEKey craftable = CraftableKeyResolver.resolve(grid.getCraftingService(), what);
        if (craftable != null) {
            openCraftAmount(player, located, craftable);
        }
    }

    private static void openCraftAmount(ServerPlayer player, LocatedTerminal located, AEKey craftable) {
        var locator = located.createMenuLocator(player);
        if (locator != null) {
            CraftAmountMenu.open(player, locator, craftable, 1);
        }
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
