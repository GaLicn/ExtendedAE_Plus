package com.extendedae_plus.ae.wireless;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.hooks.ticking.TickHandler;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 将 AE2 网格拓扑变更延迟至 tick 边界执行，与 AE2 的处理方式一致。
 * <p>
 * {@link TickHandler#addCallable} 的回调在 {@code LevelTickEvent.START} 处理，位于上一次 tick 的
 * {@code ServerTickEvent.END}（AE2 在那里结算抽电）之后、本 tick 任何方块实体与网格服务 tick 之前，
 * 因此不可能落在 {@code EnergyService.extractProviderPower} / {@code injectProviderPower} 的调用栈里。
 * <p>
 * 背景：{@code EnergyService} 在抽/注电期间用 {@code ongoingExtractOperation} /
 * {@code ongoingInjectOperation} 标志禁止增删 energy provider（{@code Preconditions.checkState}）。
 * 而 {@code IGridConnection.destroy()} / {@code IGridNode.destroy()} 会触发网格拆分与节点迁移，
 * 进而调用 {@code removeProvider} / {@code addProvider}。只要拆除动作发生在抽电回调的调用栈内
 * （例如 {@code IAEPowerStorage.extractAEPower} 调用期间破坏方块，或区块卸载嵌入抽电调用），
 * 就会抛出 {@code IllegalStateException} 打断存档。
 * <p>
 * AE2 对该状态守卫采用快速失败策略，不捕获异常或重试；网格拓扑变更通过队列延迟至 tick 边界
 * （参见 {@code MEP2PTunnelPart}、{@code CapabilityP2PTunnelPart}、
 * {@code CableBusContainer} 中对 {@code addCallable} 的用法）。此处沿用该策略：
 * 无条件延迟、仅执行一次，不捕获异常或重试。若延迟执行时仍发生异常，AE2 的
 * {@code TickHandler.processQueue} 会记录并处理该异常，不会中断存档流程。
 */
public final class DeferredGridWork {
    private DeferredGridWork() {
    }

    /**
     * 在下一个 tick 边界执行 {@code action}。
     * <p>
     * {@code level} 为 null 时加入 AE2 的全局 server 队列（{@code ServerTickEvent.END} 排空），
     * 同样不会落在抽电调用栈内。
     */
    public static void schedule(@Nullable ServerLevel level, Runnable action) {
        TickHandler.instance().addCallable(level, action);
    }

    /**
     * 仅用于促使对端尽快重新寻路；失败无副作用，因此这里不记录日志。
     */
    public static void wakeDevice(@Nullable IGridNode node) {
        if (node == null) {
            return;
        }
        try {
            var grid = node.getGrid();
            if (grid != null) {
                grid.getTickManager().wakeDevice(node);
            }
        } catch (RuntimeException ignored) {
            // 节点可能已被销毁
        }
    }

    @Nullable
    public static IGridConnection findExistingConnection(@Nullable IGridNode a, @Nullable IGridNode b) {
        if (a == null || b == null) {
            return null;
        }
        try {
            for (IGridConnection connection : a.getConnections()) {
                if (connects(connection, a, b)) {
                    return connection;
                }
            }
        } catch (RuntimeException ignored) {
            // 节点可能已被销毁
        }
        return null;
    }

    public static boolean connects(IGridConnection connection, IGridNode a, IGridNode b) {
        var ca = connection.a();
        var cb = connection.b();
        return (ca == a || cb == a) && (ca == b || cb == b);
    }
}
