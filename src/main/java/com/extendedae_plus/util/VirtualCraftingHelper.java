package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;

import java.util.Map;
import java.util.function.Function;

/**
 * 虚拟合成卡辅助工具类
 * 用于处理不同MOD的CPU虚拟完成逻辑
 */
public class VirtualCraftingHelper {

    /**
     * 检查是否应该取消整个任务
     *
     * @param tasks          任务映射
     * @param matchedProgress 匹配到的进度
     * @param valueExtractor 提取进度的函数
     * @return 是否应该取消整个任务
     */
    public static <T> boolean shouldCancelWholeJob(
            Map<IPatternDetails, T> tasks,
            T matchedProgress,
            Function<T, Long> valueExtractor) {
        if (matchedProgress == null) {
            return false;
        }
        long matchedValue = valueExtractor.apply(matchedProgress);
        if (matchedValue > 1) {
            return false;
        }

        for (var entry : tasks.entrySet()) {
            var taskProgress = entry.getValue();
            if (taskProgress == null) {
                continue;
            }

            long remaining = valueExtractor.apply(taskProgress);
            if (taskProgress == matchedProgress) {
                remaining -= 1;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * 查找匹配的进度
     *
     * @param tasks          任务映射
     * @param patternDetails 样板详情
     * @return 匹配的进度，如果没有则返回null
     */
    public static <T> T findMatchingProgress(
            Map<IPatternDetails, T> tasks,
            IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return null;
        }

        var progress = tasks.get(patternDetails);
        if (progress != null) {
            return progress;
        }

        var patternDefinition = patternDetails.getDefinition();
        for (var entry : tasks.entrySet()) {
            var taskPattern = entry.getKey();
            if (taskPattern == patternDetails) {
                return entry.getValue();
            }
            if (taskPattern != null && patternDefinition != null) {
                var taskDefinition = taskPattern.getDefinition();
                if (taskDefinition != null && taskDefinition.equals(patternDefinition)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 获取网格的合成服务
     *
     * @param mainNode 主节点
     * @return 合成服务，如果不可用则返回null
     */
    public static ICraftingService getCraftingService(IManagedGridNode mainNode) {
        if (mainNode == null) {
            return null;
        }

        var node = mainNode.getNode();
        if (node == null) {
            return null;
        }

        var grid = node.getGrid();
        if (grid == null) {
            return null;
        }

        return grid.getCraftingService();
    }

    /**
     * 虚拟完成处理器接口
     */
    public interface VirtualCompletionHandler<T extends ICraftingCPU, P> {
        /**
         * 检查CPU类型是否匹配
         */
        boolean isValidCPU(ICraftingCPU cpu);

        /**
         * 获取任务映射
         */
        Map<IPatternDetails, P> getTasks(T cpu);

        /**
         * 获取进度值
         */
        long getProgressValue(P progress);

        /**
         * 完成任务
         * @return 是否成功完成
         */
        boolean finishJob(T cpu);
    }

    /**
     * 执行虚拟完成逻辑
     *
     * @param mainNode       主节点
     * @param patternDetails 样板详情
     * @param handler        处理器
     */
    @SuppressWarnings("unchecked")
    public static <T extends ICraftingCPU, P> void executeVirtualCompletion(
            IManagedGridNode mainNode,
            IPatternDetails patternDetails,
            VirtualCompletionHandler<T, P> handler) {

        ICraftingService craftingService = getCraftingService(mainNode);
        if (craftingService == null) {
            return;
        }

        for (ICraftingCPU cpu : craftingService.getCpus()) {
            if (!cpu.isBusy()) {
                continue;
            }

            if (!handler.isValidCPU(cpu)) {
                continue;
            }

            T typedCpu = (T) cpu;
            Map<IPatternDetails, P> tasks = handler.getTasks(typedCpu);
            if (tasks == null) {
                continue;
            }

            P progress = findMatchingProgress(tasks, patternDetails);
            if (progress == null) {
                continue;
            }

            if (shouldCancelWholeJob(tasks, progress, handler::getProgressValue)) {
                if (handler.finishJob(typedCpu)) {
                    break;
                }
            }
        }
    }
}
