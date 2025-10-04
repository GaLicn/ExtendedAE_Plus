package com.extendedae_plus.network;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.parts.AEBasePart;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.util.PatternProviderDataUtil;
import com.glodblock.github.extendedae.util.FCClientUtil;
import com.glodblock.github.glodium.util.GlodUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;
import java.util.Objects;

import static com.glodblock.github.extendedae.client.render.EAEHighlightHandler.highlight;

/**
 * 客户端从 CraftingCPUScreen 发送：鼠标下条目对应的 AEKey。
 * 服务端在当前打开的 CraftingCPUMenu 所属网络中，定位匹配该 AEKey 的样板供应器，
 * 打开该供应器自身的 UI（不是目标机器的 UI）。
 */
public class CraftingMonitorOpenProviderC2SPacket implements CustomPacketPayload {
    public static final Type<CraftingMonitorOpenProviderC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "crafting_monitor_open_provider"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingMonitorOpenProviderC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> AEKey.writeKey(buf, pkt.what),
            buf -> new CraftingMonitorOpenProviderC2SPacket(AEKey.readKey(buf))
    );
    private final AEKey what;

    public CraftingMonitorOpenProviderC2SPacket(AEKey what) {
        this.what = what;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CraftingMonitorOpenProviderC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 必须在 CraftingCPU 界面内
            if (!(player.containerMenu instanceof CraftingCPUMenu menu)) {
                return;
            }

            // 通过菜单的 target（可能是 BlockEntity/Part/ItemHost），按 IActionHost 获取 Grid
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

                        // 跳过未连接到网格或不活跃的 provider（使用 util 判断并传入当前 grid）
                        if (!PatternProviderDataUtil.isProviderAvailable(ppl, grid)) continue;

                        // 直接打开供应器自身的 UI（调用 Host 默认方法）
                        try {
                            // 部件与方块实体分别选择定位器并打开界面
                            if (host instanceof AEBasePart part) {
                                host.openMenu(player, MenuLocators.forPart(part));
                                highlightWithMessage(pbe.getBlockPos(), part.getSide(), Objects.requireNonNull(pbe.getLevel()).dimension(), 1.0, player);
                            } else {
                                host.openMenu(player, MenuLocators.forBlockEntity(pbe));
                                highlightWithMessage(pbe.getBlockPos(), null, Objects.requireNonNull(pbe.getLevel()).dimension(), 1.0, player);
                            }

                            // 高亮打开的供应器位置并发送聊天提示


                            // 先在该 provider 中定位 pattern 的槽位索引，以便计算页码（尽量早退出，按槽位逐个解码）
                            int foundSlot = PatternProviderDataUtil.findSlotForPattern(ppl, pattern.getDefinition());
                            if (foundSlot >= 0) {
                                int pageId = foundSlot / 36;
                                if (pageId > 0) {
                                    // 发送 S2C：切换到指定页
                                    player.connection.send(new SetProviderPageS2CPacket(pageId));
                                }
                            }

                            // 最后发送高亮包，保证界面已打开
                            var outs = pattern.getOutputs();
                            if (outs != null && !outs.isEmpty() && outs.getFirst() != null) {
                                AEKey key = outs.getFirst().what();
                                player.connection.send(new SetPatternHighlightS2CPacket(key, true));
                            }

                            return;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
    }

    private static void highlightWithMessage(BlockPos pos, Direction face, ResourceKey<Level> dim, double multiplier, Player player) {
        if (pos == null || dim == null) {
            return;
        }
        long endTime = System.currentTimeMillis() + (long) (6000 * GlodUtil.clamp(multiplier, 1, 30));
        if (face == null) {
            highlight(pos, dim, endTime);
        } else {
            var origin = new AABB(2 / 16D, 2 / 16D, 0, 14 / 16D, 14 / 16D, 2 / 16D).move(pos);
            var center = new AABB(pos).getCenter();
            switch (face) {
                case WEST -> origin = FCClientUtil.rotor(origin, center, Direction.Axis.Y, (float) (Math.PI / 2));
                case SOUTH -> origin = FCClientUtil.rotor(origin, center, Direction.Axis.Y, (float) Math.PI);
                case EAST -> origin = FCClientUtil.rotor(origin, center, Direction.Axis.Y, (float) (-Math.PI / 2));
                case UP -> origin = FCClientUtil.rotor(origin, center, Direction.Axis.X, (float) (-Math.PI / 2));
                case DOWN -> origin = FCClientUtil.rotor(origin, center, Direction.Axis.X, (float) (Math.PI / 2));
            }
            highlight(pos, face, dim, endTime, origin);
        }

        if (player != null) {
            player.displayClientMessage(Component.translatable("chat.ex_pattern_access_terminal.pos", pos.toShortString(), dim.location().getPath()), false);
        }
    }
}
