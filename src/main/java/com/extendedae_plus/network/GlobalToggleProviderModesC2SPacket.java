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
import com.extendedae_plus.api.config.EAPSettings;
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
 * C2S 包：全局批量切换样板供应器的三种模式
 *  - 普通阻挡模式（AE2 原生 BLOCKING_MODE）
 *  - 高级阻挡模式
 *  - 智能翻倍模式
 * <p>
 * 包体包含三个操作码（每个 1 byte）以及控制器方块位置，用于定位所属 ME 网络。
 */
public class GlobalToggleProviderModesC2SPacket implements CustomPacketPayload {

    public static final Type<GlobalToggleProviderModesC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "global_toggle_provider_modes"));

    public static final StreamCodec<FriendlyByteBuf, GlobalToggleProviderModesC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeByte(packet.blockingModeOperation.id);
                buf.writeByte(packet.advancedBlockingOperation.id);
                buf.writeByte(packet.smartDoublingOperation.id);
                buf.writeBlockPos(packet.controllerBlockPos);
            },
            buf -> new GlobalToggleProviderModesC2SPacket(
                    Operation.byId(buf.readByte()),
                    Operation.byId(buf.readByte()),
                    Operation.byId(buf.readByte()),
                    buf.readBlockPos())
    );

    private final Operation blockingModeOperation;
    private final Operation advancedBlockingOperation;
    private final Operation smartDoublingOperation;

    /**
     * 发起请求的玩家的控制器位置，用于获取对应的 IGrid
     */
    private final BlockPos controllerBlockPos;

    public GlobalToggleProviderModesC2SPacket(Operation blockingModeOperation,
                                              Operation advancedBlockingOperation,
                                              Operation smartDoublingOperation,
                                              BlockPos controllerBlockPos) {
        this.blockingModeOperation = blockingModeOperation;
        this.advancedBlockingOperation = advancedBlockingOperation;
        this.smartDoublingOperation = smartDoublingOperation;
        this.controllerBlockPos = controllerBlockPos;
    }

    public static void handle(final GlobalToggleProviderModesC2SPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            var blockEntity = level.getBlockEntity(message.controllerBlockPos);

            if (!(blockEntity instanceof IInWorldGridNodeHost gridNodeHost)) return;

            var gridNode = gridNodeHost.getGridNode(null);
            if (gridNode == null) return;

            IGrid grid = gridNode.getGrid();
            if (grid == null) return;

            int affectedCount = applyToAllPatternProviders(grid, message);

            // 给发起者一个短暂的行动条提示，方便知道本次操作实际影响了多少个供应器
            player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.global_toggle_applied", affectedCount),
                    true);
        });
    }

    /**
     * 遍历当前 ME 网络中所有样板供应器（方块、零件、第三方实现），并应用切换操作
     */
    private static int applyToAllPatternProviders(IGrid grid, GlobalToggleProviderModesC2SPacket message) {
        int affectedCount = 0;
        // 用 Set 去重，因为同一个 Logic 实例可能被多种方式收集到
        Set<PatternProviderLogic> uniqueLogics = new HashSet<>();

        // 1. AE2 原生的方块实体形式（Pattern Provider BlockEntity）
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderBlockEntity.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderBlockEntity.class), uniqueLogics);

        // 2. AE2 原生的电缆零件形式（Pattern Provider Part）
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderPart.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderPart.class), uniqueLogics);

        // 3. 任何实现了 PatternProviderLogicHost 接口的机器（包括 ExtendedAE 自己的扩展）
        collectLogicsFromMachineSet(grid.getMachines(PatternProviderLogicHost.class), uniqueLogics);
        collectLogicsFromMachineSet(grid.getActiveMachines(PatternProviderLogicHost.class), uniqueLogics);

        // 4. 兼容 ExtendedAE（glodblock）自己的 ExPatternProvider（因为 AE2 的 getMachines 只按精确类匹配接口会漏）
        collectByReflection(grid, uniqueLogics, "com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
        collectByReflection(grid, uniqueLogics, "com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider");

        // 真正执行切换
        for (PatternProviderLogic logic : uniqueLogics) {
            if (applyOperationToLogic(logic, message)) {
                affectedCount++;
            }
        }
        return affectedCount;
    }

    /**
     * 工具方法：把一个 Set<? extends SomeMachine> 中的 Logic 加入去重集合
     */
    private static void collectLogicsFromMachineSet(Set<?> machineSet, Set<PatternProviderLogic> target) {
        if (machineSet == null) return;
        for (Object obj : machineSet) {
            addLogicIfPresent(target, obj);
        }
    }

    /**
     * 通过反射兼容第三方精确类（防止接口匹配漏掉）
     */
    private static void collectByReflection(IGrid grid, Set<PatternProviderLogic> target, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            collectLogicsFromMachineSet(grid.getMachines(clazz), target);
            collectLogicsFromMachineSet(grid.getActiveMachines(clazz), target);
        } catch (Throwable ignored) {
            // 如果类不存在（比如玩家没装 ExtendedAE）直接忽略
        }
    }

    /**
     * 从任意对象里尝试取出 PatternProviderLogic（兼容多种实现）
     */
    private static void addLogicIfPresent(Set<PatternProviderLogic> target, Object obj) {
        if (obj == null) return;
        try {
            if (obj instanceof PatternProviderLogicHost host && host.getLogic() != null) {
                target.add(host.getLogic());
                return;
            }
            // 兜底反射调用 getLogic()
            var method = obj.getClass().getMethod("getLogic");
            Object result = method.invoke(obj);
            if (result instanceof PatternProviderLogic logic) {
                target.add(logic);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 对单个 PatternProviderLogic 应用本次包里携带的三种操作
     */
    private static boolean applyOperationToLogic(PatternProviderLogic logic, GlobalToggleProviderModesC2SPacket message) {
        if (logic == null) return false;
        var configManager = logic.getConfigManager();
        if (configManager == null) return false;

        boolean anyChanged = false;

        // 1. AE2 原生阻挡模式
        if (message.blockingModeOperation != Operation.NOOP) {
            boolean current = isBlockingModeEnabled(logic);
            boolean target = calculateTargetState(current, message.blockingModeOperation);
            configManager.putSetting(Settings.BLOCKING_MODE, target ? YesNo.YES : YesNo.NO);
            anyChanged |= (current != target);
        }

        // 2. 高级阻挡模式
        if (message.advancedBlockingOperation != Operation.NOOP) {
            boolean current = configManager.getSetting(EAPSettings.ADVANCED_BLOCKING) == YesNo.YES;
            boolean target = calculateTargetState(current, message.advancedBlockingOperation);
            configManager.putSetting(EAPSettings.ADVANCED_BLOCKING, target ? YesNo.YES : YesNo.NO);
            anyChanged |= (current != target);
        }

        // 3. 智能翻倍模式
        if (message.smartDoublingOperation != Operation.NOOP) {
            boolean current = configManager.getSetting(EAPSettings.SMART_DOUBLING) == YesNo.YES;
            boolean target = calculateTargetState(current, message.smartDoublingOperation);
            configManager.putSetting(EAPSettings.SMART_DOUBLING, target ? YesNo.YES : YesNo.NO);
            anyChanged |= (current != target);
        }

        // 有改动时保存并让 AE2 同步到客户端
        if (anyChanged) {
            try {
                logic.saveChanges();
            } catch (Throwable ignored) {
            }
        }
        return anyChanged;
    }

    /**
     * 根据当前状态和操作码计算目标状态
     */
    private static boolean calculateTargetState(boolean currentValue, Operation operation) {
        return switch (operation) {
            case SET_TRUE -> true;
            case SET_FALSE -> false;
            case TOGGLE -> !currentValue;
            case NOOP -> currentValue;
        };
    }

    /**
     * 安全获取 AE2 原生阻挡模式状态（防止旧版本抛异常）
     */
    private static boolean isBlockingModeEnabled(PatternProviderLogic logic) {
        try {
            return logic.isBlocking();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 操作类型枚举（对应客户端发来的 1 byte）
     */
    public enum Operation {
        NOOP((byte) 0),      // 不执行任何操作
        SET_TRUE((byte) 1),  // 强制开启
        SET_FALSE((byte) 2), // 强制关闭
        TOGGLE((byte) 3);    // 切换当前状态

        public final byte id;

        Operation(byte id) {
            this.id = id;
        }

        static Operation byId(byte id) {
            return switch (id) {
                case 1 -> SET_TRUE;
                case 2 -> SET_FALSE;
                case 3 -> TOGGLE;
                default -> NOOP;
            };
        }
    }
}