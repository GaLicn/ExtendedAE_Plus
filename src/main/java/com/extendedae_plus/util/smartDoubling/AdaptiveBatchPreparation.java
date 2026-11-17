package com.extendedae_plus.util.smartDoubling;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import com.extendedae_plus.util.smartDoubling.AdaptiveBatchInventoryCache.InputTemplate;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** 为不同 CPU 派发器准备同一套自回收样板批次。 */
public final class AdaptiveBatchPreparation {
    private AdaptiveBatchPreparation() {}

    public static @Nullable PreparedBatch prepare(IPatternDetails pattern, long remainingCrafts,
            int patternLimit, int configuredLimit, long learnedLimit, @Nullable AEKey finalOutput,
            KeyCounter[] oneCraftInputs, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            ICraftingInventory inventory, Level level, Map<IPatternDetails, Long> taskCrafts) {
        if (remainingCrafts <= 1L || !PatternScaler.hasInputOutputOverlap(pattern)
                || PatternScaler.hasFinalOutputFeedback(pattern, finalOutput)) {
            return null;
        }

        Map<AEKey, Long> perCraftInputs = aggregateInputs(oneCraftInputs);
        if (perCraftInputs.isEmpty()) {
            return null;
        }

        AdaptiveBatchInventoryCache inventoryCache = new AdaptiveBatchInventoryCache(inventory, level);
        long limit = getRepresentableBatchLimit(pattern, remainingCrafts, patternLimit, configuredLimit, learnedLimit);
        long multiplier = limitBatchByInventory(limit, inventoryCache, perCraftInputs, Map.of());
        if (multiplier <= 1L) {
            return null;
        }

        if (taskCrafts.size() > 1) {
            Map<AEKey, Long> reservedInputs = collectOtherTaskInputs(taskCrafts, pattern, inventoryCache);
            if (!reservedInputs.isEmpty()) {
                multiplier = limitBatchByInventory(multiplier, inventoryCache, perCraftInputs, reservedInputs);
            }
        }
        if (multiplier <= 1L) {
            return null;
        }

        KeyCounter[] scaledInputs;
        KeyCounter scaledOutputs;
        KeyCounter scaledContainerItems;
        try {
            scaledInputs = scaleInputs(oneCraftInputs, multiplier);
            scaledOutputs = scaleCounter(expectedOutputs, multiplier);
            scaledContainerItems = scaleCounter(expectedContainerItems, multiplier);
        } catch (ArithmeticException overflow) {
            return null;
        }

        IPatternDetails scaledPattern = PatternScaler.createScaled(pattern, multiplier);
        if (scaledPattern == null || !reserveAdditionalInputs(inventory, perCraftInputs, multiplier - 1L)) {
            return null;
        }
        return new PreparedBatch(scaledPattern, scaledInputs, scaledOutputs, scaledContainerItems, multiplier);
    }

    private static long getRepresentableBatchLimit(IPatternDetails pattern, long remainingCrafts,
            int patternLimit, int configuredLimit, long learnedLimit) {
        long limit = remainingCrafts;
        if (patternLimit > 0) limit = Math.min(limit, patternLimit);
        if (configuredLimit > 0) limit = Math.min(limit, configuredLimit);
        limit = Math.min(limit, learnedLimit);
        for (var input : pattern.getInputs()) {
            if (input == null || input.getMultiplier() <= 0L) continue;
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null) continue;
            for (var possible : possibleInputs) {
                if (possible == null || possible.amount() <= 0L) continue;
                limit = Math.min(limit, Long.MAX_VALUE / possible.amount() / input.getMultiplier());
            }
        }
        for (var output : pattern.getOutputs()) {
            if (output != null && output.amount() > 0L) {
                limit = Math.min(limit, Long.MAX_VALUE / output.amount());
            }
        }
        return Math.max(1L, limit);
    }

    private static long limitBatchByInventory(long initialLimit, AdaptiveBatchInventoryCache inventoryCache,
            Map<AEKey, Long> perCraftInputs, Map<AEKey, Long> reservedInputs) {
        long limit = initialLimit;
        if (limit <= 1L) return 1L;
        for (var entry : perCraftInputs.entrySet()) {
            long available = inventoryCache.getAvailableAmount(entry.getKey());
            available = Math.max(0L, available - reservedInputs.getOrDefault(entry.getKey(), 0L));
            long additionalCopies = available / entry.getValue();
            long maxFromKey = additionalCopies >= limit - 1L ? limit : additionalCopies + 1L;
            limit = Math.min(limit, maxFromKey);
            if (limit <= 1L) return 1L;
        }
        return Math.max(1L, limit);
    }

    private static Map<AEKey, Long> collectOtherTaskInputs(Map<IPatternDetails, Long> taskCrafts,
            IPatternDetails activePattern, AdaptiveBatchInventoryCache inventoryCache) {
        Map<AEKey, Long> reserved = new LinkedHashMap<>();
        for (var task : taskCrafts.entrySet()) {
            if (task.getKey() == activePattern || task.getValue() <= 0L) continue;
            for (var input : task.getKey().getInputs()) {
                if (input == null || input.getMultiplier() <= 0L) continue;
                long remainingOperations = saturatingMultiply(input.getMultiplier(), task.getValue());
                for (InputTemplate template : inventoryCache.getValidTemplates(input)) {
                    if (remainingOperations <= 0L || template.amount() <= 0L) break;
                    long alreadyReserved = reserved.getOrDefault(template.key(), 0L);
                    long available = inventoryCache.getAvailableAmount(template.key());
                    long availableForTask = Math.max(0L, available - alreadyReserved);
                    long operations = Math.min(remainingOperations, availableForTask / template.amount());
                    if (operations <= 0L) continue;
                    long amount = operations * template.amount();
                    reserved.merge(template.key(), amount, AdaptiveBatchPreparation::saturatingAdd);
                    remainingOperations -= operations;
                }
            }
        }
        return reserved;
    }

    private static Map<AEKey, Long> aggregateInputs(KeyCounter[] inputHolder) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        try {
            for (var counter : inputHolder) {
                if (counter == null) continue;
                for (var entry : counter) {
                    if (entry.getKey() != null && entry.getLongValue() > 0L) {
                        result.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                    }
                }
            }
        } catch (ArithmeticException overflow) {
            return Map.of();
        }
        return result;
    }

    private static KeyCounter[] scaleInputs(KeyCounter[] inputHolder, long multiplier) {
        KeyCounter[] result = new KeyCounter[inputHolder.length];
        for (int i = 0; i < inputHolder.length; i++) {
            KeyCounter target = result[i] = new KeyCounter();
            for (var entry : inputHolder[i]) {
                target.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
            }
        }
        return result;
    }

    private static KeyCounter scaleCounter(KeyCounter source, long multiplier) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return result;
    }

    private static boolean reserveAdditionalInputs(ICraftingInventory inventory,
            Map<AEKey, Long> perCraftInputs, long copies) {
        KeyCounter extracted = new KeyCounter();
        try {
            for (var entry : perCraftInputs.entrySet()) {
                long amount = Math.multiplyExact(entry.getValue(), copies);
                long taken = inventory.extract(entry.getKey(), amount, Actionable.MODULATE);
                if (taken > 0L) extracted.add(entry.getKey(), taken);
                if (taken != amount) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, new KeyCounter[]{extracted});
                    return false;
                }
            }
        } catch (ArithmeticException overflow) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, new KeyCounter[]{extracted});
            return false;
        }
        return true;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record PreparedBatch(IPatternDetails pattern, KeyCounter[] inputs, KeyCounter outputs,
            KeyCounter containerItems, long multiplier) {}
}
