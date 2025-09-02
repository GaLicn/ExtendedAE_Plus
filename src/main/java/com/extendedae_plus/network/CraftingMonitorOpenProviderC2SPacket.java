package com.extendedae_plus.network;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.parts.AEBasePart;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.util.PatternProviderDataUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 客户端从 CraftingCPUScreen 发送：鼠标下条目对应的 AEKey。
 * 服务端在当前打开的 CraftingCPUMenu 所属网络中，定位匹配该 AEKey 的样板供应器，
 * 打开该供应器自身的 UI（不是目标机器的 UI）。
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
        AEKey key = AEKey.readKey(buf);
        return new CraftingMonitorOpenProviderC2SPacket(key);
    }

    public static void handle(CraftingMonitorOpenProviderC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            LogUtils.getLogger().info("EAP[S]: recv CraftingMonitorOpenProviderC2SPacket key={} from {}", msg.what, player.getGameProfile().getName());

            // 必须在 CraftingCPU 界面内
            if (!(player.containerMenu instanceof CraftingCPUMenu menu)) {
                LogUtils.getLogger().info("EAP[S]: not in CraftingCPUMenu, abort");
                return;
            }

            // 通过菜单的 target（可能是 BlockEntity/Part/ItemHost），按 IActionHost 获取 Grid
            IGrid grid = null;
            Object target = ((AEBaseMenu) menu).getTarget();
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

            // 2) 遍历提供该样板的 Provider，定位 PatternProviderLogic
            for (var pattern : patterns) {
                var providers = craftingService.getProviders(pattern);
                for (var provider : providers) {
                    if (provider instanceof PatternProviderLogic ppl) {
                        // accessor 获取 host
                        PatternProviderLogicHost host = ((PatternProviderLogicAccessor) ppl).eap$host();
                        if (host == null) continue;
                        var pbe = host.getBlockEntity();
                        if (pbe == null) continue;
                        // 在服务端上下文中执行，pbe 仅用于构造菜单定位器

                        // 直接打开供应器自身的 UI（调用 Host 默认方法）
                        try {
                            // 告知目标玩家客户端高亮该 AEKey（避免全局服务端状态污染）
                            AEKey key = pattern.getOutputs()[0].what();
                            ModNetwork.CHANNEL.sendTo(new SetPatternHighlightS2CPacket(key, true), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                            // 部件与方块实体分别选择定位器并打开界面
                            if (host instanceof AEBasePart part) {
                                host.openMenu(player, MenuLocators.forPart(part));
                            } else {
                                host.openMenu(player, MenuLocators.forBlockEntity(pbe));
                            }

                            // 先在该 provider 中定位 pattern 的槽位索引，以便计算页码
                            int foundSlot = -1;
                            var list = PatternProviderDataUtil.getAllPatternData(ppl);
                            for (var pd : list) {

                                if (pd != null && pd.getPatternDetails() != null
                                        && pd.getPatternDetails().getDefinition().equals(pattern.getDefinition())) {
                                    foundSlot = pd.getSlotIndex();
                                    break;
                                }

                            }
                            if (foundSlot >= 0) {
                                int pageId = foundSlot / 36;
                                if (pageId > 0) {
                                    // 发送 S2C 包通知客户端切换到指定页（客户端会写入 mixin 字段并重排槽位）
                                    ModNetwork.CHANNEL.sendTo(new SetProviderPageS2CPacket(pageId), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                                }
                            }
                            context.setPacketHandled(true);
                            return;
                        } catch (Throwable t) {
                            LogUtils.getLogger().error("EAP[S]: open provider UI failed at {}", pbe.getBlockPos(), t);
                        }
                    }
                }
            }

            LogUtils.getLogger().info("EAP[S]: no provider UI opened for key={}", msg.what);
        });
        context.setPacketHandled(true);
    }


}
