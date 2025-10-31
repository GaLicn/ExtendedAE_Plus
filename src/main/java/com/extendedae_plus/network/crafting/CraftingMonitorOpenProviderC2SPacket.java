package com.extendedae_plus.network.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.service.CraftingService;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.parts.AEBasePart;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.network.SetBlockHighlightS2CPacket;
import com.extendedae_plus.network.SetPatternHighlightS2CPacket;
import com.extendedae_plus.network.provider.SetProviderPageS2CPacket;
import com.extendedae_plus.util.PatternProviderDataUtil;
import com.glodblock.github.extendedae.client.render.EAEHighlightHandler;
import com.glodblock.github.glodium.util.GlodUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 网络包：客户端向服务器发送请求，打开与 AEKey 对应的 PatternProvider UI。
 * <p>
 * 流程：
 * 1. 客户端发送 AEKey。
 * 2. 服务端根据当前 CraftingCPUMenu 获取对应 Grid。
 * 3. 定位所有提供该 AEKey 的 PatternProvider。
 * 4. 打开 Provider UI，并向客户端发送高亮和页码信息。
 */
public class CraftingMonitorOpenProviderC2SPacket {
    private final AEKey what;

    public CraftingMonitorOpenProviderC2SPacket(AEKey what) {
        this.what = what;
    }

    public static void encode(CraftingMonitorOpenProviderC2SPacket msg, FriendlyByteBuf buf) {
        AEKey.writeKey(buf, msg.what);
    }

    public static CraftingMonitorOpenProviderC2SPacket decode(FriendlyByteBuf buf) {
        return new CraftingMonitorOpenProviderC2SPacket(AEKey.readKey(buf));
    }

    public static void handle(CraftingMonitorOpenProviderC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof CraftingCPUMenu menu)) return;

            // 从菜单获取 Grid
            IGrid grid = GridHelper.getGridFromMenu(menu);
            if (grid == null) return;

            // 获取 CraftingService
            var cs = grid.getCraftingService();
            if (!(cs instanceof CraftingService craftingService)) return;

            // 根据 AEKey 查找所有匹配样板
            Collection<IPatternDetails> patterns = craftingService.getCraftingFor(msg.what);
            if (patterns.isEmpty()) return;

            // 遍历所有样板，找到第一个可用 Provider 并打开 UI
            for (var pattern : patterns) {
                var provider = CraftingMonitorOpenProviderC2SPacket.PatternLocator.findValidProvider(craftingService, pattern, grid);
                if (provider == null) {
                    for (var pd : craftingService.getProviders(pattern)) {
                        if (pd instanceof com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine machine) {
                            gtmOpenUI(machine, player, pattern);
                            return;
                        } else if (pd instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine machine) {
                            gtmOpenUI(machine, player, pattern);
                            return;
                        }
                    }
                }

                try {
                    ProviderUIHelper.openProviderUI(provider, pattern, player);
                } catch (Exception ignored) {}
            }
        });
        context.setPacketHandled(true);
    }

    // ===================== 内部工具类 =====================

    /**
     * 兼容gtm
     *
     * @param machine 样板总成
     * @param player  玩家
     * @param pattern 样板
     */
    private static void gtmOpenUI(MetaMachine machine, ServerPlayer player, IPatternDetails pattern) {
        try {
            BlockPos pos = machine.getPos();
            Level level = machine.getLevel();
            if (pos == null || level == null) return;
            if (!level.isClientSide) { // 确保在服务器端执行
                MachineUIFactory.INSTANCE.openUI(MetaMachine.getMachine(level, pos), player);
            }

            // 聊天提示
            player.displayClientMessage(
                    Component.translatable(
                            "chat.extendedae_plus.terminal.pos",
                            pos.toShortString(),
                            level.dimension().location().getPath(),
                            (int) Math.sqrt(player.blockPosition().distSqr(pos))
                    ),
                    false
            );

            // 最后发送高亮包，保证界面已打开
            if (pattern.getOutputs() != null && pattern.getOutputs().length > 0 && pattern.getOutputs()[0] != null) {
                AEKey key = pattern.getOutputs()[0].what();
                ModNetwork.CHANNEL.sendTo(new SetPatternHighlightS2CPacket(key, true), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }

            EAEHighlightHandler.highlight(pos, level.dimension(), System.currentTimeMillis() + 15000);
        } catch (Exception ignored) {
        }
    }

    /**
     * GridHelper: 从菜单中获取网格实例
     */
    private static final class GridHelper {
        private GridHelper() {}

        /**
         * 获取菜单对应的 Grid
         *
         * @param menu 当前 AEBaseMenu
         * @return Grid 或 null
         */
        private static IGrid getGridFromMenu(AEBaseMenu menu) {
            Object target = menu.getTarget();
            if (target instanceof IActionHost host && host.getActionableNode() != null) {
                return host.getActionableNode().getGrid();
            }
            return null;
        }
    }

    /**
     * PatternLocator: 根据样板定位可用的 Provider
     */
    private static final class PatternLocator {
        private PatternLocator() {}

        /**
         * 查找提供指定样板的可用 Provider
         *
         * @param cs      CraftingService
         * @param pattern 样板
         * @param grid    当前 Grid
         * @return 第一个可用的 PatternProviderLogic 或 null
         */
        private static PatternProviderLogic findValidProvider(CraftingService cs, IPatternDetails pattern, IGrid grid) {
            var providers = cs.getProviders(pattern);
            for (var provider : providers) {
                if (provider instanceof PatternProviderLogic ppl) {
                    var host = ((PatternProviderLogicAccessor) ppl).eap$host();
                    if (host == null || host.getBlockEntity() == null) continue;
                    if (!PatternProviderDataUtil.isProviderAvailable(ppl, grid)) continue;
                    return ppl;
                }
            }
            return null;
        }
    }

    /**
     * ProviderUIHelper: 打开 Provider UI 并发送客户端反馈
     */
    private static final class ProviderUIHelper {
        private ProviderUIHelper() {}

        /**
         * 打开 Provider UI
         * 1. 打开菜单
         * 2. 发送高亮包
         * 3. 发送页码包
         * 4. 发送 Pattern 输出高亮包
         *
         * @param provider PatternProviderLogic 实例
         * @param pattern  样板
         * @param player   玩家
         */
        private static void openProviderUI(PatternProviderLogic provider, IPatternDetails pattern, ServerPlayer player) {
            var host = ((PatternProviderLogicAccessor) provider).eap$host();
            var pbe = host.getBlockEntity();
            if (pbe == null) return;

            boolean isPart = host instanceof AEBasePart part;
            var locator = isPart ? MenuLocators.forPart((AEBasePart) host) : MenuLocators.forBlockEntity(pbe);
            host.openMenu(player, locator);

            // 高亮显示
            ModNetwork.CHANNEL.sendTo(
                    new SetBlockHighlightS2CPacket(
                            pbe.getBlockPos(),
                            isPart ? ((AEBasePart) host).getSide() : null,
                            pbe.getLevel().dimension().location(),
                            (long) (6000 * GlodUtil.clamp(1.0, 1, 30))
                    ),
                    player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
            );

            // 聊天提示
            player.displayClientMessage(
                    Component.translatable(
                            "chat.extendedae_plus.terminal.pos",
                            pbe.getBlockPos().toShortString(),
                            pbe.getLevel().dimension().location().getPath(),
                            (int) Math.sqrt(player.blockPosition().distSqr(host.getBlockEntity().getBlockPos()))
                    ),
                    false
            );

            // 页码同步
            int slot = PatternProviderDataUtil.findSlotForPattern(provider, pattern.getDefinition());
            if (slot >= 0) {
                int page = slot / 36;
                if (page > 0) {
                    ModNetwork.CHANNEL.sendTo(
                            new SetProviderPageS2CPacket(page),
                            player.connection.connection,
                            NetworkDirection.PLAY_TO_CLIENT
                    );
                }
            }

            // 输出高亮
            var outputs = pattern.getOutputs();
            if (outputs != null && outputs.length > 0 && outputs[0] != null) {
                AEKey key = outputs[0].what();
                ModNetwork.CHANNEL.sendTo(
                        new SetPatternHighlightS2CPacket(key, true),
                        player.connection.connection,
                        NetworkDirection.PLAY_TO_CLIENT
                );
            }
        }
    }
}
