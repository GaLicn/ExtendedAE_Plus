package com.extendedae_plus.network;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;

/**
 * 客户端从 CraftingCPUScreen 发送：鼠标下条目对应的 AEKey。
 * 服务端在当前打开的 CraftingCPUMenu 所属网络中，定位匹配该 AEKey 的样板供应器，
 * 尝试打开其目标机器的 GUI。
 */
public class CraftingMonitorJumpC2SPacket implements CustomPacketPayload {
    public static final Type<CraftingMonitorJumpC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "crafting_monitor_jump"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingMonitorJumpC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> AEKey.writeKey(buf, pkt.what),
            buf -> new CraftingMonitorJumpC2SPacket(AEKey.readKey(buf))
    );
    private final AEKey what;

    public CraftingMonitorJumpC2SPacket(AEKey what) {
        this.what = what;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CraftingMonitorJumpC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            LogUtils.getLogger().info("EAP[S]: recv CraftingMonitorJumpC2SPacket key={} from {}", msg.what, player.getGameProfile().getName());

            // 必须在 CraftingCPU 界面内
            if (!(player.containerMenu instanceof appeng.menu.me.crafting.CraftingCPUMenu menu)) {
                LogUtils.getLogger().info("EAP[S]: not in CraftingCPUMenu, abort");
                return;
            }

            // 通过菜单 target（可能是 BlockEntity/Part/ItemHost）按 IActionHost 获取 Grid
            IGrid grid = null;
            Object target = ((appeng.menu.AEBaseMenu) menu).getTarget();
            if (target instanceof IActionHost host && host.getActionableNode() != null) {
                grid = host.getActionableNode().getGrid();
            }
            if (grid == null) {
                LogUtils.getLogger().info("EAP[S]: grid is null, abort");
                return;
            }

            var cs = grid.getCraftingService();
            if (!(cs instanceof CraftingService craftingService)) {
                LogUtils.getLogger().info("EAP[S]: craftingService is null/unsupported, abort");
                return;
            }

            // 1) 根据 AEKey 找到可能的样板（pattern）
            Collection<IPatternDetails> patterns = craftingService.getCraftingFor(msg.what);
            LogUtils.getLogger().info("EAP[S]: patterns found={} for key={}", patterns.size(), msg.what);
            if (patterns.isEmpty()) {
                return;
            }

            // 2) 遍历提供该样板的 Provider，优先 PatternProviderLogic
            for (var pattern : patterns) {
                var providers = craftingService.getProviders(pattern);
                int providerCount = 0;
                for (var provider : providers) {
                    providerCount++;
                    try {
                        LogUtils.getLogger().info("EAP[S]: provider class={}", provider.getClass().getName());
                    } catch (Throwable ignored) {}
                    if (provider instanceof PatternProviderLogic ppl) {
                        // 使用 accessor 获取 host（受保护字段通过 accessor 访问）
                        PatternProviderLogicHost host = ((PatternProviderLogicAccessor) ppl).eap$host();
                        if (host == null) continue;
                        var pbe = host.getBlockEntity();
                        ServerLevel serverLevel = player.serverLevel();

                        // 尝试对邻居打开 GUI（优先通过 MenuProvider）
                        for (Direction dir : host.getTargets()) {
                            BlockPos targetPos = pbe.getBlockPos().relative(dir);
                            var tbe = serverLevel.getBlockEntity(targetPos);
                            if (tbe instanceof MenuProvider provider1) {
                                LogUtils.getLogger().info("EAP[S]: open screen via MenuProvider at {}", targetPos);
                                player.openMenu(provider1, targetPos);
                                return;
                            }
                            var tstate = serverLevel.getBlockState(targetPos);
                            var provider2 = tstate.getMenuProvider(serverLevel, targetPos);
                            if (provider2 != null) {
                                LogUtils.getLogger().info("EAP[S]: open screen via state.getMenuProvider at {}", targetPos);
                                player.openMenu(provider2, targetPos);
                                return;
                            }
                        }

                        // 兜底：若无 MenuProvider，则跳过（不再模拟右键以确保兼容性）
                    }
                }
                LogUtils.getLogger().info("EAP[S]: providers count for one pattern: {}", providerCount);
            }
            LogUtils.getLogger().info("EAP[S]: no target opened for key={}", msg.what);
        });
    }
}
