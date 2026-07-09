package com.extendedae_plus.content.matrix.supermatrix;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.AutoCraftingMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuperAssemblerMatrixCluster {

    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private final List<SuperAssemblerMatrixPart> parts = new ArrayList<>();
    private final List<PatternCorePlusBlockEntity> patternCores = new ArrayList<>();
    private final Map<AEItemKey, BatchTask> batchQueue = new LinkedHashMap<>();
    private final List<VirtualThreadTask> fallbackThreads = new ArrayList<>();
    private final Object2LongLinkedOpenHashMap<AEKey> outputBuffer = new Object2LongLinkedOpenHashMap<>();

    private SuperAssemblerMatrixBlockEntity core;
    private BatchPlan activeBatch;
    private boolean destroyed;
    private int crafterCoreCount;
    private int speedCoreCount;
    private int uploadCoreCount;
    private long displayedConcurrentExecutions;
    private long displayedConcurrentUntil;

    public SuperAssemblerMatrixCluster(BlockPos boundsMin, BlockPos boundsMax) {
        this.boundsMin = boundsMin.immutable();
        this.boundsMax = boundsMax.immutable();
    }

    public void addPart(SuperAssemblerMatrixPart part) {
        this.parts.add(part);
        if (this.core == null && part instanceof SuperAssemblerMatrixBlockEntity blockEntity) {
            this.core = blockEntity;
        }
        if (part instanceof PatternCorePlusBlockEntity patternCore) {
            this.patternCores.add(patternCore);
        } else if (part instanceof CrafterCorePlusBlockEntity) {
            this.crafterCoreCount++;
        } else if (part instanceof SpeedCorePlusBlockEntity) {
            this.speedCoreCount++;
        } else if (part instanceof UploadCoreBlockEntity) {
            this.uploadCoreCount++;
        }
    }

    public void done() {
        if (this.core != null) {
            this.core.setCore(true);
        }
        for (var part : this.parts) {
            part.eap$setSuperMatrixCluster(this);
            part.eap$updateSuperMatrixStatus();
        }
        if (this.core != null) {
            this.core.refreshCraftingProvider();
        }
    }

    public void destroy() {
        this.destroy(ExtendedAEPlus.isServerStopping());
    }

    public void destroyQuietly() {
        this.destroy(true);
    }

    private void destroy(boolean quiet) {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        for (var part : this.parts) {
            if (part.eap$getSuperMatrixCluster() == this) {
                part.eap$setSuperMatrixCluster(null);
                if (part instanceof SuperAssemblerMatrixBlockEntity blockEntity) {
                    blockEntity.setCore(false);
                }
                if (!quiet) {
                    part.eap$updateSuperMatrixStatus();
                }
            }
        }
        if (!quiet && this.core != null) {
            this.core.refreshCraftingProvider();
        }
    }

    public SuperAssemblerMatrixStats getStats() {
        return new SuperAssemblerMatrixStats(
                this.crafterCoreCount,
                this.patternCores.size(),
                this.speedCoreCount,
                this.uploadCoreCount
        );
    }

    public java.util.Iterator<IGridNode> getGridNodes() {
        var nodes = new ArrayList<IGridNode>();
        for (var part : this.parts) {
            if (part instanceof SuperAssemblerMatrixBlockEntity blockEntity) {
                var node = blockEntity.getMainNode().getNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        return nodes.iterator();
    }

    public boolean isBusy() {
        return this.destroyed || this.core == null || this.getStats().parallelBudget() <= 0;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    public InternalInventory[] getPatternInventories() {
        var inventories = new InternalInventory[this.patternCores.size()];
        for (int i = 0; i < this.patternCores.size(); i++) {
            inventories[i] = this.patternCores.get(i).getPatternInventory();
        }
        return inventories;
    }

    public List<PatternCorePlusBlockEntity> getPatternCores() {
        return java.util.Collections.unmodifiableList(this.patternCores);
    }

    public long getConcurrentExecutions() {
        if (this.destroyed || this.core == null) {
            return 0;
        }
        long current = this.computeConcurrentExecutions();
        if (current > 0) {
            return this.isConcurrentDisplayActive()
                    ? Math.max(current, this.displayedConcurrentExecutions)
                    : current;
        }
        return this.isConcurrentDisplayActive() ? this.displayedConcurrentExecutions : 0;
    }

    private long computeConcurrentExecutions() {
        if (this.destroyed || this.core == null) {
            return 0;
        }
        long count = this.activeBatch == null ? 0 : this.activeBatch.batchSize;
        count += this.fallbackThreads.size();

        // 批处理可能 1tick 内完成，UI 同步时 activeBatch 已清空；把已接单待执行量也计入显示。
        if (this.activeBatch == null && !this.batchQueue.isEmpty()) {
            long pendingBatch = 0;
            for (var task : this.batchQueue.values()) {
                pendingBatch += task.pending;
            }
            count += Math.min(pendingBatch, this.getStats().parallelBudget());
        }
        return count;
    }

    private void rememberConcurrentExecutions() {
        long current = this.computeConcurrentExecutions();
        if (current > 0) {
            // 1tick 批处理会产生瞬时尾批，UI 参考原版线程占用口径保留近期峰值，避免数字来回跳。
            this.displayedConcurrentExecutions = this.isConcurrentDisplayActive()
                    ? Math.max(this.displayedConcurrentExecutions, current)
                    : current;
            this.displayedConcurrentUntil = this.getGameTime() + 20;
        }
    }

    private boolean isConcurrentDisplayActive() {
        return this.displayedConcurrentExecutions > 0 && this.getGameTime() <= this.displayedConcurrentUntil;
    }

    private long getGameTime() {
        return this.core != null && this.core.getLevel() != null ? this.core.getLevel().getGameTime() : 0;
    }

    public void cancelWork() {
        this.batchQueue.clear();
        this.fallbackThreads.clear();
        this.outputBuffer.clear();
        this.activeBatch = null;
        this.displayedConcurrentExecutions = 0;
        this.displayedConcurrentUntil = 0;
    }

    public void refreshCraftingProvider() {
        if (this.core != null) {
            this.core.refreshCraftingProvider();
        }
    }

    public List<IPatternDetails> getAvailablePatterns() {
        if (this.destroyed || this.core == null || this.patternCores.isEmpty()) {
            return List.of();
        }
        var patterns = new ArrayList<IPatternDetails>();
        var level = this.core.getLevel();
        if (level == null) {
            return List.of();
        }
        for (var patternCore : this.patternCores) {
            for (var stack : patternCore.getPatternInventory()) {
                if (stack.getItem() instanceof EncodedPatternItem) {
                    var details = PatternDetailsHelper.decodePattern(stack, level);
                    if (details instanceof IMolecularAssemblerSupportedPattern) {
                        patterns.add(StrictMolecularAssemblerPattern.wrap((IMolecularAssemblerSupportedPattern) details));
                    }
                }
            }
        }
        return patterns;
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.isBusy() || this.core == null || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }

        var copiedInputs = copyCounters(inputHolder);
        var consumedGrid = new ItemStack[9];
        if (!fillCraftingGrid(pattern, copiedInputs, consumedGrid)) {
            return false;
        }

        // 真正接单前才消费 AE 传入的输入计数，避免分类失败时污染输入。
        if (!fillCraftingGrid(pattern, inputHolder, new ItemStack[9])) {
            return false;
        }

        var job = AcceptedJob.create(pattern, consumedGrid, this.core);
        if (job == null) {
            return false;
        }

        if (job.batchable()) {
            this.batchQueue.compute(job.definition(), (definition, task) -> {
                if (task == null) {
                    return new BatchTask(job.definition(), job.outputKey(), job.outputAmount(), 1);
                }
                task.pending++;
                return task;
            });
        } else {
            this.fallbackThreads.add(new VirtualThreadTask(pattern, consumedGrid));
        }
        this.rememberConcurrentExecutions();
        this.core.wakeCoreNode();
        return true;
    }

    public boolean tick(int ticksSinceLastCall) {
        if (this.destroyed || this.core == null) {
            return false;
        }
        boolean worked = false;
        worked |= this.flushOutputBuffer();
        worked |= this.tickBatch(ticksSinceLastCall);
        worked |= this.tickFallbackThreads(ticksSinceLastCall);
        if (this.computeConcurrentExecutions() > 0) {
            this.rememberConcurrentExecutions();
        }
        return worked || this.hasPendingWork();
    }

    private boolean hasPendingWork() {
        return this.activeBatch != null || !this.batchQueue.isEmpty()
                || !this.fallbackThreads.isEmpty() || !this.outputBuffer.isEmpty();
    }

    private boolean tickBatch(int ticksSinceLastCall) {
        var stats = this.getStats();
        if (stats.parallelBudget() <= 0) {
            return false;
        }

        if (this.activeBatch == null) {
            var iterator = this.batchQueue.values().iterator();
            if (iterator.hasNext()) {
                var task = iterator.next();
                long batchSize = Math.min(task.pending, stats.parallelBudget());
                task.pending -= batchSize;
                if (task.pending <= 0) {
                    iterator.remove();
                }
                this.activeBatch = new BatchPlan(task.outputKey, task.outputAmount, batchSize, 0);
                this.rememberConcurrentExecutions();
            }
        }

        if (this.activeBatch == null) {
            return false;
        }

        this.activeBatch.progress += Math.max(1, ticksSinceLastCall);
        if (this.activeBatch.progress >= stats.singleCraftTicks()) {
            long amount = this.activeBatch.outputAmount * this.activeBatch.batchSize;
            this.outputBuffer.addTo(this.activeBatch.outputKey, amount);
            this.activeBatch = null;
        }
        return true;
    }

    private boolean tickFallbackThreads(int ticksSinceLastCall) {
        if (this.fallbackThreads.isEmpty()) {
            return false;
        }
        var stats = this.getStats();
        var iterator = this.fallbackThreads.iterator();
        while (iterator.hasNext()) {
            var task = iterator.next();
            task.progress += Math.max(1, ticksSinceLastCall);
            if (task.progress >= stats.singleCraftTicks()) {
                this.finishVirtualThread(task);
                iterator.remove();
            }
        }
        return true;
    }

    private void finishVirtualThread(VirtualThreadTask task) {
        if (this.core == null || this.core.getLevel() == null) {
            return;
        }
        var input = createCraftingInput(task.grid);
        var output = task.pattern.assemble(input, this.core.getLevel());
        var genericOutput = GenericStack.fromItemStack(output);
        if (genericOutput != null) {
            this.outputBuffer.addTo(genericOutput.what(), genericOutput.amount());
        }
        for (var remainder : task.pattern.getRemainingItems(input)) {
            var genericRemainder = GenericStack.fromItemStack(remainder);
            if (genericRemainder != null) {
                this.outputBuffer.addTo(genericRemainder.what(), genericRemainder.amount());
            }
        }
    }

    private boolean flushOutputBuffer() {
        if (this.core == null || this.outputBuffer.isEmpty()) {
            return false;
        }
        boolean worked = false;
        for (var iterator = this.outputBuffer.object2LongEntrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            long inserted = this.core.insertToNetwork(entry.getKey(), entry.getLongValue());
            if (inserted > 0) {
                worked = true;
                long remaining = entry.getLongValue() - inserted;
                if (remaining <= 0) {
                    iterator.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }
        return worked;
    }

    private static KeyCounter[] copyCounters(KeyCounter[] inputHolder) {
        var copied = new KeyCounter[inputHolder.length];
        for (int i = 0; i < inputHolder.length; i++) {
            copied[i] = new KeyCounter();
            copied[i].addAll(inputHolder[i]);
        }
        return copied;
    }

    private static boolean fillCraftingGrid(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputs,
            ItemStack[] grid) {
        for (int i = 0; i < grid.length; i++) {
            grid[i] = ItemStack.EMPTY;
        }
        pattern.fillCraftingGrid(inputs, (slot, stack) -> grid[slot] = stack.copy());
        for (var counter : inputs) {
            counter.removeZeros();
            if (!counter.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static CraftingContainer createCraftingInput(ItemStack[] grid) {
        var container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int i = 0; i < grid.length; i++) {
            var stack = grid[i];
            container.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return container;
    }

    private record AcceptedJob(
            AEItemKey definition,
            AEKey outputKey,
            long outputAmount,
            boolean batchable
    ) {
        private static @Nullable AcceptedJob create(IMolecularAssemblerSupportedPattern pattern, ItemStack[] grid,
                SuperAssemblerMatrixBlockEntity core) {
            var level = core.getLevel();
            if (level == null) {
                return null;
            }
            var input = createCraftingInput(grid);
            var output = pattern.assemble(input, level);
            var genericOutput = GenericStack.fromItemStack(output);
            if (genericOutput == null) {
                return null;
            }
            boolean hasRemainder = false;
            for (var remainder : pattern.getRemainingItems(input)) {
                if (!remainder.isEmpty()) {
                    hasRemainder = true;
                    break;
                }
            }
            return new AcceptedJob(pattern.getDefinition(), genericOutput.what(), genericOutput.amount(), !hasRemainder);
        }
    }

    private static final class BatchTask {
        private final AEItemKey definition;
        private final AEKey outputKey;
        private final long outputAmount;
        private long pending;

        private BatchTask(AEItemKey definition, AEKey outputKey, long outputAmount, long pending) {
            this.definition = definition;
            this.outputKey = outputKey;
            this.outputAmount = outputAmount;
            this.pending = pending;
        }
    }

    private static final class BatchPlan {
        private final AEKey outputKey;
        private final long outputAmount;
        private final long batchSize;
        private int progress;

        private BatchPlan(AEKey outputKey, long outputAmount, long batchSize, int progress) {
            this.outputKey = outputKey;
            this.outputAmount = outputAmount;
            this.batchSize = batchSize;
            this.progress = progress;
        }
    }

    private static final class VirtualThreadTask {
        private final IMolecularAssemblerSupportedPattern pattern;
        private final ItemStack[] grid;
        private int progress;

        private VirtualThreadTask(IMolecularAssemblerSupportedPattern pattern, ItemStack[] grid) {
            this.pattern = pattern;
            this.grid = grid;
        }
    }
}
