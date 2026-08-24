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
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.util.PatternProviderDataUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;

/** Opens the provider that owns a pattern shown in the crafting monitor. */
public final class CraftingMonitorOpenProviderC2SPacket implements CustomPacketPayload {
    public static final Type<CraftingMonitorOpenProviderC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "crafting_monitor_open_provider"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingMonitorOpenProviderC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> AEKey.writeKey(buf, packet.what),
                    buf -> new CraftingMonitorOpenProviderC2SPacket(AEKey.readKey(buf)));

    private final AEKey what;

    public CraftingMonitorOpenProviderC2SPacket(AEKey what) {
        this.what = what;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftingMonitorOpenProviderC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftingCPUMenu menu)) {
                return;
            }

            IGrid grid = getGrid(menu);
            if (grid == null || !(grid.getCraftingService() instanceof CraftingService craftingService)) {
                return;
            }

            Collection<IPatternDetails> patterns = craftingService.getCraftingFor(packet.what);
            for (IPatternDetails pattern : patterns) {
                PatternProviderLogic provider = findValidProvider(craftingService, pattern, grid);
                if (provider != null && openProviderUI(provider, pattern, player)) {
                    return;
                }
            }
        });
    }

    private static IGrid getGrid(AEBaseMenu menu) {
        Object target = menu.getTarget();
        if (target instanceof IActionHost host && host.getActionableNode() != null) {
            return host.getActionableNode().getGrid();
        }
        return null;
    }

    private static PatternProviderLogic findValidProvider(CraftingService craftingService,
                                                            IPatternDetails pattern,
                                                            IGrid grid) {
        for (var provider : craftingService.getProviders(pattern)) {
            if (!(provider instanceof PatternProviderLogic logic)) {
                continue;
            }

            PatternProviderLogicHost host = ((PatternProviderLogicAccessor) logic).eap$host();
            BlockEntity blockEntity = host == null ? null : host.getBlockEntity();
            if (blockEntity == null || blockEntity instanceof MirrorPatternProviderBlockEntity) {
                continue;
            }
            if (!PatternProviderDataUtil.isProviderAvailable(logic, grid)) {
                continue;
            }
            return logic;
        }
        return null;
    }

    private static boolean openProviderUI(PatternProviderLogic provider,
                                          IPatternDetails pattern,
                                          ServerPlayer player) {
        PatternProviderLogicHost host = ((PatternProviderLogicAccessor) provider).eap$host();
        BlockEntity blockEntity = host == null ? null : host.getBlockEntity();
        if (host == null || blockEntity == null || blockEntity.getLevel() == null) {
            return false;
        }

        try {
            if (host instanceof AEBasePart part) {
                host.openMenu(player, MenuLocators.forPart(part));
                PacketDistributor.sendToPlayer(player, new SetBlockHighlightS2CPacket(
                        blockEntity.getBlockPos(),
                        part.getSide(),
                        blockEntity.getLevel().dimension().location(),
                        6000));
            } else {
                host.openMenu(player, MenuLocators.forBlockEntity(blockEntity));
                PacketDistributor.sendToPlayer(player, new SetBlockHighlightS2CPacket(
                        blockEntity.getBlockPos(),
                        null,
                        blockEntity.getLevel().dimension().location(),
                        6000));
            }

            player.displayClientMessage(Component.translatable(
                    "chat.ex_pattern_access_terminal.pos",
                    blockEntity.getBlockPos().toShortString(),
                    blockEntity.getLevel().dimension().location().getPath(),
                    (int) Math.sqrt(player.blockPosition().distSqr(blockEntity.getBlockPos()))), false);

            int slot = PatternProviderDataUtil.findSlotForPattern(provider, pattern.getDefinition());
            if (slot >= 0) {
                int page = slot / 36;
                if (page > 0) {
                    PacketDistributor.sendToPlayer(player, new SetProviderPageS2CPacket(page));
                }
            }

            var outputs = pattern.getOutputs();
            if (outputs != null && !outputs.isEmpty() && outputs.get(0) != null) {
                PacketDistributor.sendToPlayer(player, new SetPatternHighlightS2CPacket(outputs.get(0).what(), true));
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
