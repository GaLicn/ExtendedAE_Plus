package com.extendedae_plus.network.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 客户端从 CraftingCPUScreen 发送：鼠标下条目对应的 AEKey。
 * 服务端在当前打开的 CraftingCPUMenu 所属网络中，定位匹配该 AEKey 的样板供应器，
 * 尝试打开其目标机器的 GUI。
 */
public class CraftingMonitorJumpC2SPacket {
    private final AEKey what;

    public CraftingMonitorJumpC2SPacket(AEKey what) {
        this.what = what;
    }

    public static void encode(CraftingMonitorJumpC2SPacket msg, FriendlyByteBuf buf) {
        AEKey.writeKey(buf, msg.what);
    }

    public static CraftingMonitorJumpC2SPacket decode(FriendlyByteBuf buf) {
        AEKey key = AEKey.readKey(buf);
        return new CraftingMonitorJumpC2SPacket(key);
    }

    public static void handle(CraftingMonitorJumpC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // 必须在 CraftingCPU 界面内
            if (!(player.containerMenu instanceof CraftingCPUMenu menu)) {
                return;
            }

            // 通过菜单 target（可能是 BlockEntity/Part/ItemHost）按 IActionHost 获取 Grid
            IGrid grid = null;
            Object target = menu.getTarget();
            if (target instanceof IActionHost host && host.getActionableNode() != null) {
                grid = host.getActionableNode().getGrid();
            }
            if (grid == null) {
                return;
            }

            var cs = grid.getCraftingService();
            if (!(cs instanceof CraftingService craftingService)) {
                return;
            }

            // 1) 根据 AEKey 找到可能的样板（pattern）
            Collection<IPatternDetails> patterns = craftingService.getCraftingFor(msg.what);
            if (patterns.isEmpty()) {
                return;
            }

            // 2) 遍历提供该样板的 Provider，优先 PatternProviderLogic
            for (var pattern : patterns) {
                var providers = craftingService.getProviders(pattern);
                for (var provider : providers) {
                    if (provider instanceof PatternProviderLogic ppl) {
                        // 使用 accessor 获取 host（受保护字段通过 accessor 访问）
                        PatternProviderLogicHost host = ((PatternProviderLogicAccessor) ppl).eap$host();
                        if (host == null) continue;
                        var pbe = host.getBlockEntity();
                        ServerLevel serverLevel = player.serverLevel();

                        // 尝试对邻居打开 GUI（复用 OpenProviderUiC2SPacket 的策略）
                        for (Direction dir : host.getTargets()) {
                            BlockPos targetPos = pbe.getBlockPos().relative(dir);
                            var tbe = serverLevel.getBlockEntity(targetPos);
                            if (tbe instanceof MenuProvider provider1) {
                                NetworkHooks.openScreen(player, provider1, targetPos);
                                context.setPacketHandled(true);
                                return;
                            }
                            var tstate = serverLevel.getBlockState(targetPos);
                            var provider2 = tstate.getMenuProvider(serverLevel, targetPos);
                            if (provider2 != null) {
                                NetworkHooks.openScreen(player, provider2, targetPos);
                                context.setPacketHandled(true);
                                return;
                            }
                        }

                        // 兜底：若无 MenuProvider，始终模拟一次右键（优先有方块实体的一面）
                        InteractionHand hand = player.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.MAIN_HAND;
                        Direction chosen = null;
                        for (Direction d : host.getTargets()) {
                            if (serverLevel.getBlockEntity(pbe.getBlockPos().relative(d)) != null) { chosen = d; break; }
                        }
                        if (chosen == null) {
                            for (Direction d : host.getTargets()) {
                                if (!serverLevel.getBlockState(pbe.getBlockPos().relative(d)).isAir()) { chosen = d; break; }
                            }
                        }
                        if (chosen != null) {
                            BlockPos targetPos = pbe.getBlockPos().relative(chosen);
                            var state2 = serverLevel.getBlockState(targetPos);
                            var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), chosen.getOpposite(), targetPos, false);
                            InteractionResult r = state2.use(serverLevel, player, hand, hit);
                            if (r.consumesAction()) {
                                context.setPacketHandled(true);
                                return;
                            }
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
