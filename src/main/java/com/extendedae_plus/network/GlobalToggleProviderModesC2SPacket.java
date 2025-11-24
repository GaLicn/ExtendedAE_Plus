package com.extendedae_plus.network;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking;
import com.extendedae_plus.api.smartDoubling.ISmartDoubling;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * C2S：全网批量切换样板供应器的三种模式：
 * - 阻挡模式（AE2 内置 BLOCKING_MODE 设置）
 * - 高级阻挡模式（IAdvancedBlocking mixin）
 * - 智能翻倍模式（ISmartDoubling mixin）
 *
 * 负载为三个操作码（各1字节），分别对应：blocking、advancedBlocking、smartDoubling。
 */
public class GlobalToggleProviderModesC2SPacket implements CustomPacketPayload {
    public static final Type<GlobalToggleProviderModesC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "global_toggle_provider_modes"));

    public static final StreamCodec<FriendlyByteBuf, GlobalToggleProviderModesC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeByte(pkt.opBlocking.id);
                buf.writeByte(pkt.opAdvancedBlocking.id);
                buf.writeByte(pkt.opSmartDoubling.id);
                buf.writeBlockPos(pkt.controllerPos);
            },
            buf -> new GlobalToggleProviderModesC2SPacket(Op.byId(buf.readByte()), Op.byId(buf.readByte()), Op.byId(buf.readByte()), buf.readBlockPos())
    );
    private final Op opBlocking;
    private final Op opAdvancedBlocking;
    private final Op opSmartDoubling;
    private final BlockPos controllerPos;
    public GlobalToggleProviderModesC2SPacket(Op opBlocking, Op opAdvancedBlocking, Op opSmartDoubling, BlockPos controllerPos) {
        this.opBlocking = opBlocking;
        this.opAdvancedBlocking = opAdvancedBlocking;
        this.opSmartDoubling = opSmartDoubling;
        this.controllerPos = controllerPos;
    }

    public static void handle(final GlobalToggleProviderModesC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player == null) return;

            // 从控制方块实体的 AE2 节点确定 AE 网络上下文
            var level = player.serverLevel();
            var be = level.getBlockEntity(msg.controllerPos);
            if (!(be instanceof IInWorldGridNodeHost host)) return;
            var node = host.getGridNode(null);
            if (node == null) return;
            IGrid grid = node.getGrid();
            if (grid == null) return;

            int affected = applyToAllProviders(grid, msg);
            // 向发起玩家反馈影响数量，便于判断按钮是否生效
            player.displayClientMessage(Component.literal("E+ 全局切换已应用到 " + affected + " 个样板供应器"), true);
        });
    }

    private static int applyToAllProviders(IGrid grid, GlobalToggleProviderModesC2SPacket msg) {
        int affected = 0;
        // 去重集合，避免同一逻辑重复计数
        Set<PatternProviderLogic> all = new HashSet<>();

        // 方块形式的样板供应器（全部/在线）
        try {
            Set<PatternProviderBlockEntity> blocksAll = grid.getMachines(PatternProviderBlockEntity.class);
            Set<PatternProviderBlockEntity> blocksActive = grid.getActiveMachines(PatternProviderBlockEntity.class);
            for (PatternProviderBlockEntity be : blocksAll) if (be != null && be.getLogic() != null) all.add(be.getLogic());
            for (PatternProviderBlockEntity be : blocksActive) if (be != null && be.getLogic() != null) all.add(be.getLogic());
        } catch (Throwable ignored) {}

        // Part 形式的样板供应器（全部/在线）
        try {
            Set<PatternProviderPart> partsAll = grid.getMachines(PatternProviderPart.class);
            Set<PatternProviderPart> partsActive = grid.getActiveMachines(PatternProviderPart.class);
            for (PatternProviderPart part : partsAll) if (part != null && part.getLogic() != null) all.add(part.getLogic());
            for (PatternProviderPart part : partsActive) if (part != null && part.getLogic() != null) all.add(part.getLogic());
        } catch (Throwable ignored) {}

        // 兼容：任意实现了 PatternProviderLogicHost 的机器（例如 ExtendedAE 的 PartExPatternProvider）
        try {
            Set<PatternProviderLogicHost> hostsAll = grid.getMachines(PatternProviderLogicHost.class);
            Set<PatternProviderLogicHost> hostsActive = grid.getActiveMachines(PatternProviderLogicHost.class);
            for (PatternProviderLogicHost host : hostsAll) if (host != null && host.getLogic() != null) all.add(host.getLogic());
            for (PatternProviderLogicHost host : hostsActive) if (host != null && host.getLogic() != null) all.add(host.getLogic());
        } catch (Throwable ignored) {}

        // 兼容：显式匹配第三方具体类（通过反射），避免 AE2 仅按精确类型匹配导致 interface 不返回的问题
        collectByClassName(grid, all, "com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
        collectByClassName(grid, all, "com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider");

        for (PatternProviderLogic logic : all) {
            if (applyToLogic(logic, msg)) affected++;
        }
        return affected;
    }

    private static void collectByClassName(IGrid grid, Set<PatternProviderLogic> out, String className) {
        try {
            Class<?> cls = Class.forName(className);
            // 收集全部与在线两类机器
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
            // 兜底：若对象有 getLogic 方法且返回 PatternProviderLogic
            var m = o.getClass().getMethod("getLogic");
            Object ret = m.invoke(o);
            if (ret instanceof PatternProviderLogic logic) out.add(logic);
        } catch (Throwable ignored) {}
    }

    private static boolean applyToLogic(PatternProviderLogic logic, GlobalToggleProviderModesC2SPacket msg) {
        if (logic == null) return false;
        boolean changed = false;
        // 1) 阻挡模式（AE2 内置设置）
        if (msg.opBlocking != Op.NOOP) {
            boolean current = safeIsBlocking(logic);
            boolean target = computeTarget(current, msg.opBlocking);
            var cm = logic.getConfigManager();
            if (cm != null) {
                cm.putSetting(Settings.BLOCKING_MODE, target ? YesNo.YES : YesNo.NO);
                changed = changed || (current != target);
            }
        }
        // 2) 高级阻挡（mixin 接口）
        if (msg.opAdvancedBlocking != Op.NOOP && logic instanceof IAdvancedBlocking adv) {
            boolean current = adv.eap$getAdvancedBlocking();
            boolean target = computeTarget(current, msg.opAdvancedBlocking);
            adv.eap$setAdvancedBlocking(target);
            changed = changed || (current != target);
        }
        // 3) 智能翻倍（mixin 接口）
        if (msg.opSmartDoubling != Op.NOOP && logic instanceof ISmartDoubling sd) {
            boolean current = sd.eap$getSmartDoubling();
            boolean target = computeTarget(current, msg.opSmartDoubling);
            sd.eap$setSmartDoubling(target);
            changed = changed || (current != target);
        }
        // 保存更改并让 AE2 同步
        if (changed) {
            try { logic.saveChanges(); } catch (Throwable ignored) {}
        }
        return changed;
    }

    private static boolean computeTarget(boolean current, Op op) {
        return switch (op) {
            case SET_TRUE -> true;
            case SET_FALSE -> false;
            case TOGGLE -> !current;
            default -> current;
        };
    }

    private static boolean safeIsBlocking(PatternProviderLogic logic) {
        try { return logic.isBlocking(); } catch (Throwable t) { return false; }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Op {
        NOOP((byte) 0),
        SET_TRUE((byte) 1),
        SET_FALSE((byte) 2),
        TOGGLE((byte) 3);
        public final byte id;

        Op(byte id) {this.id = id;}

        static Op byId(byte id) {
            return switch (id) {
                case 1 -> SET_TRUE;
                case 2 -> SET_FALSE;
                case 3 -> TOGGLE;
                default -> NOOP;
            };
        }
    }
}
