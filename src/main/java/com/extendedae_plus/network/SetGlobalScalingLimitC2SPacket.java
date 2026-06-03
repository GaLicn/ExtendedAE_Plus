package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * C2S: 设置网络中所有样板供应器的翻倍限制值
 */
public class SetGlobalScalingLimitC2SPacket implements CustomPacketPayload {

    public static final Type<SetGlobalScalingLimitC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_global_scaling_limit"));

    public static final StreamCodec<FriendlyByteBuf, SetGlobalScalingLimitC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.limit);
                buf.writeBlockPos(packet.controllerPos);
            },
            buf -> new SetGlobalScalingLimitC2SPacket(buf.readInt(), buf.readBlockPos())
    );

    private final int limit;
    private final BlockPos controllerPos;

    public SetGlobalScalingLimitC2SPacket(int limit, BlockPos controllerPos) {
        this.limit = limit;
        this.controllerPos = controllerPos;
    }

    public static void handle(final SetGlobalScalingLimitC2SPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            var blockEntity = level.getBlockEntity(message.controllerPos);

            if (!(blockEntity instanceof IInWorldGridNodeHost gridNodeHost)) return;

            var gridNode = gridNodeHost.getGridNode(null);
            if (gridNode == null) return;

            IGrid grid = gridNode.getGrid();
            if (grid == null) return;

            int affectedCount = applyToAllPatternProviders(grid, message.limit);

            player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.global_scaling_limit_applied", affectedCount, message.limit),
                    true);
        });
    }

    private static int applyToAllPatternProviders(IGrid grid, int limit) {
        int affectedCount = 0;
        Set<PatternProviderLogic> uniqueLogics = new HashSet<>();

        // 1. AE2 原生的方块实体形式
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderBlockEntity.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderBlockEntity.class), uniqueLogics);

        // 2. AE2 原生的电缆零件形式
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderPart.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderPart.class), uniqueLogics);

        // 3. 任何实现了 PatternProviderLogicHost 接口的机器
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderLogicHost.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderLogicHost.class), uniqueLogics);

        // 4. 兼容 ExtendedAE
        collectByReflection(grid, uniqueLogics, "com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
        collectByReflection(grid, uniqueLogics, "com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider");

        for (PatternProviderLogic logic : uniqueLogics) {
            if (applyToLogic(logic, limit)) {
                affectedCount++;
            }
        }
        return affectedCount;
    }

    private static void collectLogicsFromMachineSet(Set<?> machineSet, Set<PatternProviderLogic> target) {
        if (machineSet == null) return;
        for (Object obj : machineSet) {
            addLogicIfPresent(target, obj);
        }
    }

    private static void collectByReflection(IGrid grid, Set<PatternProviderLogic> target, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            collectLogicsFromMachineSet(grid.getMachines(clazz), target);
            collectLogicsFromMachineSet(grid.getActiveMachines(clazz), target);
        } catch (Throwable ignored) {}
    }

    private static void addLogicIfPresent(Set<PatternProviderLogic> target, Object obj) {
        if (obj == null) return;
        try {
            if (obj instanceof PatternProviderLogicHost host && host.getLogic() != null) {
                target.add(host.getLogic());
                return;
            }
            var method = obj.getClass().getMethod("getLogic");
            Object result = method.invoke(obj);
            if (result instanceof PatternProviderLogic logic) {
                target.add(logic);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean applyToLogic(PatternProviderLogic logic, int limit) {
        if (logic instanceof ISmartDoublingHolder holder) {
            holder.eap$setProviderSmartDoublingLimit(limit);
            try {
                logic.saveChanges();
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
