package com.extendedae_plus.network.provider;

import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * C2S: 设置网络中所有样板供应器的翻倍限制值
 */
public class SetGlobalScalingLimitC2SPacket {
    private final int limit;
    private final BlockPos controllerPos;

    public SetGlobalScalingLimitC2SPacket(int limit, BlockPos controllerPos) {
        this.limit = limit;
        this.controllerPos = controllerPos;
    }

    public static void encode(SetGlobalScalingLimitC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.limit);
        buf.writeBlockPos(msg.controllerPos);
    }

    public static SetGlobalScalingLimitC2SPacket decode(FriendlyByteBuf buf) {
        int limit = buf.readInt();
        BlockPos pos = buf.readBlockPos();
        return new SetGlobalScalingLimitC2SPacket(limit, pos);
    }

    public static void handle(SetGlobalScalingLimitC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            var level = player.serverLevel();
            var be = level.getBlockEntity(msg.controllerPos);
            if (!(be instanceof NetworkPatternControllerBlockEntity controller)) return;
            var node = controller.getGridNode(null);
            if (node == null) return;
            IGrid grid = node.getGrid();
            if (grid == null) return;

            int affected = applyToAllProviders(grid, msg.limit);
            player.displayClientMessage(Component.translatable("extendedae_plus.chat.pattern_provider.global_scaling_limit_applied", affected, msg.limit), true);
        });
        ctx.setPacketHandled(true);
    }

    private static int applyToAllProviders(IGrid grid, int limit) {
        int affected = 0;
        Set<PatternProviderLogic> all = new HashSet<>();

        // 方块形式的样板供应器
        try {
            Set<PatternProviderBlockEntity> blocksAll = grid.getMachines(PatternProviderBlockEntity.class);
            Set<PatternProviderBlockEntity> blocksActive = grid.getActiveMachines(PatternProviderBlockEntity.class);
            for (PatternProviderBlockEntity be : blocksAll) if (be != null && be.getLogic() != null) all.add(be.getLogic());
            for (PatternProviderBlockEntity be : blocksActive) if (be != null && be.getLogic() != null) all.add(be.getLogic());
        } catch (Throwable ignored) {}

        // Part 形式的样板供应器
        try {
            Set<PatternProviderPart> partsAll = grid.getMachines(PatternProviderPart.class);
            Set<PatternProviderPart> partsActive = grid.getActiveMachines(PatternProviderPart.class);
            for (PatternProviderPart part : partsAll) if (part != null && part.getLogic() != null) all.add(part.getLogic());
            for (PatternProviderPart part : partsActive) if (part != null && part.getLogic() != null) all.add(part.getLogic());
        } catch (Throwable ignored) {}

        // 兼容 PatternProviderLogicHost
        try {
            Set<PatternProviderLogicHost> hostsAll = grid.getMachines(PatternProviderLogicHost.class);
            Set<PatternProviderLogicHost> hostsActive = grid.getActiveMachines(PatternProviderLogicHost.class);
            for (PatternProviderLogicHost host : hostsAll) if (host != null && host.getLogic() != null) all.add(host.getLogic());
            for (PatternProviderLogicHost host : hostsActive) if (host != null && host.getLogic() != null) all.add(host.getLogic());
        } catch (Throwable ignored) {}

        // 兼容 ExtendedAE
        collectByClassName(grid, all, "com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
        collectByClassName(grid, all, "com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider");

        for (PatternProviderLogic logic : all) {
            if (applyToLogic(logic, limit)) affected++;
        }
        return affected;
    }

    private static void collectByClassName(IGrid grid, Set<PatternProviderLogic> out, String className) {
        try {
            Class<?> cls = Class.forName(className);
            Set<?> all = grid.getMachines((Class) cls);
            Set<?> active = grid.getActiveMachines((Class) cls);
            for (Object o : all) addLogicIfPresent(out, o);
            for (Object o : active) addLogicIfPresent(out, o);
        } catch (Throwable ignored) {}
    }

    private static void addLogicIfPresent(Set<PatternProviderLogic> out, Object o) {
        try {
            if (o instanceof PatternProviderLogicHost host) {
                var logic = host.getLogic();
                if (logic != null) out.add(logic);
                return;
            }
            var m = o.getClass().getMethod("getLogic");
            Object ret = m.invoke(o);
            if (ret instanceof PatternProviderLogic logic) out.add(logic);
        } catch (Throwable ignored) {}
    }

    private static boolean applyToLogic(PatternProviderLogic logic, int limit) {
        if (logic instanceof ISmartDoublingHolder holder) {
            holder.eap$setProviderSmartDoublingLimit(limit);
            logic.saveChanges();
            return true;
        }
        return false;
    }
}
