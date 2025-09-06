package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.util.WirelessTerminalLocator;
import com.extendedae_plus.util.WirelessTerminalLocator.LocatedTerminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PickFromWirelessC2SPacket implements CustomPacketPayload {
    public static final Type<PickFromWirelessC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "pick_from_wireless"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickFromWirelessC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeEnum(pkt.face);
                buf.writeDouble(pkt.hitLoc.x);
                buf.writeDouble(pkt.hitLoc.y);
                buf.writeDouble(pkt.hitLoc.z);
            },
            buf -> new PickFromWirelessC2SPacket(
                    buf.readBlockPos(),
                    buf.readEnum(Direction.class),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            )
    );

    private final BlockPos pos;
    private final Direction face;
    private final Vec3 hitLoc;

    public PickFromWirelessC2SPacket(BlockPos pos, Direction face, Vec3 hitLoc) {
        this.pos = pos;
        this.face = face;
        this.hitLoc = hitLoc;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final PickFromWirelessC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.isCreative()) return;

            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(msg.pos);
            if (state == null || state.isAir()) return;

            // 服务端权威：定位玩家任意槽位的无线终端（含 Curios）
            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            if (terminal.isEmpty()) return;

            IGrid grid;
            // 统一走 AE2 原生路径处理（包含 Curios 情况）：
            WirelessCraftingTerminalItem wct = terminal.getItem() instanceof WirelessCraftingTerminalItem c ? c : null;
            WirelessTerminalItem wt = wct != null ? wct : (terminal.getItem() instanceof WirelessTerminalItem t ? t : null);
            if (wt == null) return;
            grid = wt.getLinkedGrid(terminal, level, null);
            if (grid == null) return;
            if (!wt.hasPower(player, 0.5, terminal)) return;

            // 计算 pick 对应的物品：使用客户端实际命中位置，保证多部件方块能返回正确克隆物品
            BlockHitResult bhr = new BlockHitResult(msg.hitLoc, msg.face, msg.pos, true);
            ItemStack picked = state.getBlock().getCloneItemStack(state, bhr, level, msg.pos, player);
            if (picked.isEmpty()) {
                picked = state.getBlock().asItem().getDefaultInstance();
            }
            if (picked.isEmpty()) return;

            int targetMax = picked.getMaxStackSize();
            AEItemKey targetKey = AEItemKey.of(picked);

            IEnergyService energy = grid.getEnergyService();
            MEStorage storage = grid.getStorageService().getInventory();

            ItemStack inHand = player.getMainHandItem();
            var inv = player.getInventory();

            boolean handIsSameItem = !inHand.isEmpty() && AEItemKey.of(inHand).equals(targetKey);
            boolean placeToMainHand = inHand.isEmpty() || (handIsSameItem && inHand.getCount() < inHand.getMaxStackSize());

            int space;
            if (placeToMainHand) {
                space = inHand.isEmpty() ? targetMax : Math.min(targetMax, inHand.getMaxStackSize() - inHand.getCount());
            } else {
                int free = inv.getFreeSlot();
                if (free == -1) return;
                space = targetMax;
            }

            if (space <= 0) return;

            long extracted = StorageHelper.poweredExtraction(energy, storage, targetKey, space, new PlayerSource(player));
            if (extracted <= 0) return;

            if (placeToMainHand) {
                if (inHand.isEmpty()) {
                    inv.setItem(inv.selected, targetKey.toStack((int) extracted));
                } else {
                    int add = (int) Math.min(extracted, inHand.getMaxStackSize() - inHand.getCount());
                    if (add > 0) {
                        inHand.grow(add);
                        inv.setItem(inv.selected, inHand);
                    }
                }
            } else {
                int free = inv.getFreeSlot();
                if (free == -1) {
                    StorageHelper.poweredInsert(energy, storage, targetKey, extracted, new PlayerSource(player));
                    return;
                }
                inv.setItem(free, targetKey.toStack((int) extracted));
            }

            WirelessCraftingTerminalItem wct2 = terminal.getItem() instanceof WirelessCraftingTerminalItem c2 ? c2 : null;
            WirelessTerminalItem wt2 = wct2 != null ? wct2 : (terminal.getItem() instanceof WirelessTerminalItem t2 ? t2 : null);
            if (wt2 != null) {
                wt2.usePower(player, Math.max(0.5, extracted * 0.05), terminal);
            }
            located.commit();
            player.containerMenu.broadcastChanges();
        });
    }
}
