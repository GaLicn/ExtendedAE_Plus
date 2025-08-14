package com.extendedae_plus.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.helpers.PlayerSource;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.extendedae_plus.util.WirelessTerminalLocator;
import com.extendedae_plus.util.WirelessTerminalLocator.LocatedTerminal;

public class PickFromWirelessC2SPacket {
    private final BlockPos pos;
    private final Direction face;

    public PickFromWirelessC2SPacket(BlockPos pos, Direction face) {
        this.pos = pos;
        this.face = face;
    }

    public static void encode(PickFromWirelessC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.face);
    }

    public static PickFromWirelessC2SPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        return new PickFromWirelessC2SPacket(pos, face);
    }

    public static void handle(PickFromWirelessC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isCreative()) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(msg.pos);
            if (state == null || state.isAir()) {
                return;
            }

            // 服务端权威：定位玩家任意槽位的无线终端（含 Curios）
            LocatedTerminal located = WirelessTerminalLocator.find(player);
            ItemStack terminal = located.stack;
            WirelessTerminalItem wt = terminal.getItem() instanceof WirelessTerminalItem w ? w : null;
            if (wt == null || terminal.isEmpty()) {
                return;
            }

            // 校验网络与电量
            IGrid grid = wt.getLinkedGrid(terminal, level, player);
            if (grid == null) {
                return;
            }
            if (!wt.hasPower(player, 0.5, terminal)) {
                return;
            }

            // 计算 pick 对应的物品
            BlockHitResult bhr = new BlockHitResult(player.position(), msg.face, msg.pos, true);
            ItemStack picked = state.getBlock().getCloneItemStack(state, bhr, level, msg.pos, player);
            if (picked.isEmpty()) {
                // 兜底用方块本身
                picked = state.getBlock().asItem().getDefaultInstance();
            }
            if (picked.isEmpty()) {
                return;
            }

            int targetMax = picked.getMaxStackSize();
            AEItemKey targetKey = AEItemKey.of(picked);

            IEnergyService energy = grid.getEnergyService();
            MEStorage storage = grid.getStorageService().getInventory();

            ItemStack inHand = player.getMainHandItem();

            // 若主手有物品：尝试将其移动到玩家背包的空槽位；若没有空位则中止
            if (!inHand.isEmpty()) {
                var inv = player.getInventory();
                int free = inv.getFreeSlot();
                if (free == -1) {
                    return; // 背包已满，不进行拉取
                }
                // 将主手整组移动到空槽位
                inv.setItem(free, inHand.copy());
                inv.setItem(inv.selected, ItemStack.EMPTY);
            }

            // 现在主手应为空：拉取目标物品，尽量填满一组
            int space = targetMax; // 主手为空，目标为一整组
            long extracted = StorageHelper.poweredExtraction(energy, storage, targetKey, space, new PlayerSource(player));
            if (extracted <= 0) {
                return;
            }

            player.getInventory().setItem(player.getInventory().selected, targetKey.toStack((int) extracted));
            wt.usePower(player, Math.max(0.5, extracted * 0.05), terminal);
            // 确保写回（若位于 Curios 等需要显式写回的容器）
            located.commit();
            player.containerMenu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
