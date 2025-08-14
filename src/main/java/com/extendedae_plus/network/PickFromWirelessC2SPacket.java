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
import net.minecraft.world.phys.Vec3;
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
    private final Vec3 hitLoc;

    public PickFromWirelessC2SPacket(BlockPos pos, Direction face, Vec3 hitLoc) {
        this.pos = pos;
        this.face = face;
        this.hitLoc = hitLoc;
    }

    public static void encode(PickFromWirelessC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.face);
        buf.writeDouble(msg.hitLoc.x);
        buf.writeDouble(msg.hitLoc.y);
        buf.writeDouble(msg.hitLoc.z);
    }

    public static PickFromWirelessC2SPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new PickFromWirelessC2SPacket(pos, face, new Vec3(x, y, z));
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

            // 计算 pick 对应的物品：使用客户端实际命中位置，保证多部件方块（AE2 CableBus/部件）能返回正确克隆物品
            BlockHitResult bhr = new BlockHitResult(msg.hitLoc, msg.face, msg.pos, true);
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
            var inv = player.getInventory();

            // 决定放置目标：
            // 1) 若主手为空 -> 放主手，空间为整组
            // 2) 若主手为同一物品且未满 -> 合并到主手，空间为主手剩余空间
            // 3) 其他情况（主手不为空且不是同物品）-> 放入背包空槽，空间为整组
            boolean handIsSameItem = !inHand.isEmpty() && AEItemKey.of(inHand).equals(targetKey);
            boolean placeToMainHand = inHand.isEmpty() || (handIsSameItem && inHand.getCount() < inHand.getMaxStackSize());

            int space;
            if (placeToMainHand) {
                space = inHand.isEmpty() ? targetMax : Math.min(targetMax, inHand.getMaxStackSize() - inHand.getCount());
            } else {
                int free = inv.getFreeSlot();
                if (free == -1) {
                    return; // 背包已满，不进行拉取
                }
                space = targetMax;
            }

            if (space <= 0) {
                return;
            }

            long extracted = StorageHelper.poweredExtraction(energy, storage, targetKey, space, new PlayerSource(player));
            if (extracted <= 0) {
                return;
            }

            if (placeToMainHand) {
                if (inHand.isEmpty()) {
                    inv.setItem(inv.selected, targetKey.toStack((int) extracted));
                } else {
                    // 合并到主手
                    int add = (int) Math.min(extracted, inHand.getMaxStackSize() - inHand.getCount());
                    if (add > 0) {
                        inHand.grow(add);
                        inv.setItem(inv.selected, inHand); // 写回以确保同步
                    }
                }
            } else {
                int free = inv.getFreeSlot();
                if (free == -1) {
                    // 理论上不会发生（上面已判断），为安全起见：将提取物退回网络
                    StorageHelper.poweredInsert(energy, storage, targetKey, extracted, new PlayerSource(player));
                    return;
                }
                inv.setItem(free, targetKey.toStack((int) extracted));
            }

            wt.usePower(player, Math.max(0.5, extracted * 0.05), terminal);
            // 确保写回（若位于 Curios 等需要显式写回的容器）
            located.commit();
            player.containerMenu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
