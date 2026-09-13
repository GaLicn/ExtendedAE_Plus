package com.extendedae_plus_gtladd.network;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.SetPatternHighlightS2CPacket;
import com.glodblock.github.extendedae.client.render.EAEHighlightHandler;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;

import java.util.Collection;
import java.util.function.Supplier;

public class CraftingMonitorOpenGTMProviderC2SPacket {
    private final AEKey what;

    public CraftingMonitorOpenGTMProviderC2SPacket(AEKey what) {
        this.what = what;
    }

    public static void encode(CraftingMonitorOpenGTMProviderC2SPacket msg, FriendlyByteBuf buf) {
        AEKey.writeKey(buf, msg.what);
    }

    public static CraftingMonitorOpenGTMProviderC2SPacket decode(FriendlyByteBuf buf) {
        return new CraftingMonitorOpenGTMProviderC2SPacket(AEKey.readKey(buf));
    }

    public static void handle(CraftingMonitorOpenGTMProviderC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player != null) {
                if (player.containerMenu instanceof CraftingCPUMenu menu) {
                    IGrid grid = CraftingMonitorOpenGTMProviderC2SPacket.GridHelper.getGridFromMenu(menu);
                    if (grid == null) {
                        return;
                    }

                    ICraftingService cs = grid.getCraftingService();
                    if (!(cs instanceof CraftingService craftingService)) {
                        return;
                    }

                    Collection<IPatternDetails> patterns = craftingService.getCraftingFor(msg.what);
                    if (patterns.isEmpty()) {
                        return;
                    }

                    for (IPatternDetails pattern : patterns) {
                        for (ICraftingProvider pd : craftingService.getProviders(pattern)) {
                            if (pd instanceof MEPatternBufferPartMachine machine) {
                                gtmOpenUI(machine, player, pattern);
                                return;
                            }

                            if (pd instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine) {
                                org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine machine = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine)pd;
                                gtmOpenUI(machine, player, pattern);
                                return;
                            }

                            if (pd instanceof MEMolecularAssemblerIOPartMachine machine) {
                                gtmOpenUI(machine, player, pattern);
                                return;
                            }
                        }
                    }
                }
            }

        });
        context.setPacketHandled(true);
    }

    private static void gtmOpenUI(MetaMachine machine, ServerPlayer player, IPatternDetails pattern) {
        try {
            BlockPos pos = machine.getPos();
            Level level = machine.getLevel();

            if (pos == null || level == null) {
                return;
            }

            if (!level.isClientSide) {
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
            if (pattern.getOutputs() != null && pattern.getOutputs().length > 0 && pattern.getOutputs()[0] != null) {
                AEKey key = pattern.getOutputs()[0].what();
                ModNetwork.CHANNEL.sendTo(
                        new SetPatternHighlightS2CPacket(key, true),
                        player.connection.connection,
                        NetworkDirection.PLAY_TO_CLIENT);
            }

            EAEHighlightHandler.highlight(pos, level.dimension(), System.currentTimeMillis() + 15000L);
        } catch (Exception ignored) {
        }

    }

    private static final class GridHelper {
        private static IGrid getGridFromMenu(AEBaseMenu menu) {
            Object target = menu.getTarget();
            if (target instanceof IActionHost host) {
                if (host.getActionableNode() != null) {
                    return host.getActionableNode().getGrid();
                }
            }

            return null;
        }
    }
}
