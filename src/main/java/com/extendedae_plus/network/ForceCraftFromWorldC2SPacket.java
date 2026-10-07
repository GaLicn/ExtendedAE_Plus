package com.extendedae_plus.network;

import appeng.api.stacks.AEItemKey;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：对世界方块强制下单。
 *
 * <p>与 {@link PickFromWirelessC2SPacket} 的区别在于本包不执行网络存量提取：
 * 命中后直接打开 AE2 下单界面。用于目标物品在 AE 网络中已有存量、按普通
 * 取方块路径只会被直接拉取而无法进入下单流程的场合。</p>
 *
 * <p>是否可合成由服务端权威判定：不可合成时回一条动作栏提示，避免组合键
 * 按下后无任何反馈。</p>
 */
public class ForceCraftFromWorldC2SPacket implements CustomPacketPayload {
    public static final Type<ForceCraftFromWorldC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "force_craft_from_world"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForceCraftFromWorldC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeEnum(pkt.face);
                buf.writeDouble(pkt.hitLoc.x);
                buf.writeDouble(pkt.hitLoc.y);
                buf.writeDouble(pkt.hitLoc.z);
            },
            buf -> new ForceCraftFromWorldC2SPacket(
                    buf.readBlockPos(),
                    buf.readEnum(Direction.class),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            )
    );

    private final BlockPos pos;
    private final Direction face;
    private final Vec3 hitLoc;

    public ForceCraftFromWorldC2SPacket(BlockPos pos, Direction face, Vec3 hitLoc) {
        this.pos = pos;
        this.face = face;
        this.hitLoc = hitLoc;
    }

    public static void handle(final ForceCraftFromWorldC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.isCreative()) return;

            var level = player.serverLevel();
            BlockState state = level.getBlockState(msg.pos);
            if (state == null || state.isAir()) return;

            // 服务端权威：定位玩家任意槽位的无线终端（含 Curios）
            var located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) return;

            // 使用终端实现提供的连接逻辑，避免绕过 WTLib 的量子桥判断。
            var grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            if (grid == null) return;

            // 以客户端实际命中位置计算克隆物品，保证多部件方块返回正确物品
            BlockHitResult bhr = new BlockHitResult(msg.hitLoc, msg.face, msg.pos, true);
            ItemStack picked = state.getBlock().getCloneItemStack(state, bhr, level, msg.pos, player);
            if (picked.isEmpty()) {
                picked = state.getBlock().asItem().getDefaultInstance();
            }
            if (picked.isEmpty()) return;

            AEItemKey key = AEItemKey.of(picked);
            if (key == null) return;

            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(key)) {
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.force_craft.not_craftable"), true);
                return;
            }

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, key, 64);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
