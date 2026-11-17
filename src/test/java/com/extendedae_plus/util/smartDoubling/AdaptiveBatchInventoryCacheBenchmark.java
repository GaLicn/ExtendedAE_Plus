package com.extendedae_plus.util.smartDoubling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdaptiveBatchInventoryCacheBenchmark {
    private static final int GROUP_COUNT = 8;
    private static final int KEYS_PER_GROUP = 4;
    private static final int TASK_COUNT = 768;
    private static final int INPUTS_PER_TASK = 3;
    private static final int POSSIBLE_INPUTS_PER_SLOT = 3;
    private static final int WARMUP_ITERATIONS = 12;
    private static final int MEASURE_ITERATIONS = 40;
    private static final int MEASURE_ROUNDS = 7;
    private static volatile int blackhole;

    private AdaptiveBatchInventoryCacheBenchmark() {}

    public static void main(String[] args) {
        List<String> keys = createKeys();
        List<Demand> demands = createDemands(keys);
        SyntheticInventory inventory = createInventory(keys);

        inventory.resetMetrics();
        Map<String, Long> before = collectWithoutCache(demands, inventory);
        Metrics beforeMetrics = inventory.metrics();
        inventory.resetMetrics();
        Map<String, Long> after = collectWithCache(demands, inventory);
        Metrics afterMetrics = inventory.metrics();

        require(before.equals(after), "Cached reservation differs from the existing algorithm");
        require(afterMetrics.fuzzyScans < beforeMetrics.fuzzyScans, "Expected fewer fuzzy-template scans");
        require(afterMetrics.fuzzyKeysVisited < beforeMetrics.fuzzyKeysVisited, "Expected fewer fuzzy-key visits");
        require(afterMetrics.simulatedExtractions < beforeMetrics.simulatedExtractions,
                "Expected fewer simulated inventory queries");

        long[] beforeNanos = new long[MEASURE_ROUNDS];
        long[] afterNanos = new long[MEASURE_ROUNDS];
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            benchmarkOnce(demands, inventory, false);
            benchmarkOnce(demands, inventory, true);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            if ((i & 1) == 0) {
                beforeNanos[i] = benchmarkMany(demands, inventory, false);
                afterNanos[i] = benchmarkMany(demands, inventory, true);
            } else {
                afterNanos[i] = benchmarkMany(demands, inventory, true);
                beforeNanos[i] = benchmarkMany(demands, inventory, false);
            }
        }

        double beforeMicros = median(beforeNanos) / (double) MEASURE_ITERATIONS / 1_000.0;
        double afterMicros = median(afterNanos) / (double) MEASURE_ITERATIONS / 1_000.0;
        System.out.printf("Workload: %d tasks, %d inputs/task, %d possible inputs/slot, %d stock keys%n",
                TASK_COUNT, INPUTS_PER_TASK, POSSIBLE_INPUTS_PER_SLOT, keys.size());
        System.out.printf("Per collection: fuzzy scans %d -> %d; key visits %d -> %d; SIMULATE lookups %d -> %d%n",
                beforeMetrics.fuzzyScans, afterMetrics.fuzzyScans,
                beforeMetrics.fuzzyKeysVisited, afterMetrics.fuzzyKeysVisited,
                beforeMetrics.simulatedExtractions, afterMetrics.simulatedExtractions);
        System.out.printf("Median collection time: %.1f -> %.1f us (%.1f%%)%n",
                beforeMicros, afterMicros, 100.0 * (beforeMicros - afterMicros) / beforeMicros);
        System.out.println("Reservation equivalence: PASS");
    }

    private static List<String> createKeys() {
        List<String> keys = new ArrayList<>(GROUP_COUNT * KEYS_PER_GROUP);
        for (int group = 0; group < GROUP_COUNT; group++) {
            for (int member = 0; member < KEYS_PER_GROUP; member++) {
                keys.add("key-" + group + "-" + member);
            }
        }
        return keys;
    }

    private static List<Demand> createDemands(List<String> keys) {
        List<Demand> demands = new ArrayList<>(TASK_COUNT * INPUTS_PER_TASK);
        for (int task = 0; task < TASK_COUNT; task++) {
            for (int input = 0; input < INPUTS_PER_TASK; input++) {
                List<PossibleInput> possibleInputs = new ArrayList<>(POSSIBLE_INPUTS_PER_SLOT);
                Set<String> validKeys = new HashSet<>();
                int firstGroup = (task * 7 + input * 3) % GROUP_COUNT;
                for (int candidate = 0; candidate < POSSIBLE_INPUTS_PER_SLOT; candidate++) {
                    int group = (firstGroup + candidate * 3) % GROUP_COUNT;
                    possibleInputs.add(new PossibleInput(keys.get(group * KEYS_PER_GROUP), candidate + 1L));
                    for (int member = 0; member < KEYS_PER_GROUP; member++) {
                        validKeys.add(keys.get(group * KEYS_PER_GROUP + member));
                    }
                }
                demands.add(new Demand(1_000_000L, possibleInputs, validKeys));
            }
        }
        return demands;
    }

    private static SyntheticInventory createInventory(List<String> keys) {
        SyntheticInventory inventory = new SyntheticInventory(keys);
        for (String key : keys) inventory.amounts.put(key, 50_000L);
        return inventory;
    }

    private static Map<String, Long> collectWithoutCache(List<Demand> demands, SyntheticInventory inventory) {
        Map<String, Long> reserved = new LinkedHashMap<>();
        for (Demand demand : demands) {
            List<PossibleTemplate> candidates = new ArrayList<>();
            for (PossibleInput possible : demand.possibleInputs) {
                for (String key : inventory.findFuzzyTemplates(possible.key)) {
                    candidates.add(new PossibleTemplate(key, possible.amount));
                }
            }
            long remainingOperations = demand.operations;
            for (PossibleTemplate template : candidates) {
                if (remainingOperations <= 0L) break;
                if (!demand.validKeys.contains(template.key) || template.amount <= 0L) continue;
                long alreadyReserved = reserved.getOrDefault(template.key, 0L);
                long available = inventory.getAvailableAmount(template.key);
                long availableForTask = Math.max(0L, available - alreadyReserved);
                long operations = Math.min(remainingOperations, availableForTask / template.amount);
                if (operations <= 0L) continue;
                long amount = operations * template.amount;
                reserved.merge(template.key, amount, AdaptiveBatchInventoryCacheBenchmark::saturatingAdd);
                remainingOperations -= operations;
            }
        }
        return reserved;
    }

    private static Map<String, Long> collectWithCache(List<Demand> demands, SyntheticInventory inventory) {
        AdaptiveBatchQueryCache<String> cache = new AdaptiveBatchQueryCache<>(
                inventory::findFuzzyTemplates, inventory::getAvailableAmount);
        Map<String, Long> reserved = new LinkedHashMap<>();
        for (Demand demand : demands) {
            List<PossibleTemplate> candidates = new ArrayList<>();
            for (PossibleInput possible : demand.possibleInputs) {
                for (String key : cache.getFuzzyTemplates(possible.key)) {
                    candidates.add(new PossibleTemplate(key, possible.amount));
                }
            }
            long remainingOperations = demand.operations;
            for (PossibleTemplate template : candidates) {
                if (remainingOperations <= 0L) break;
                if (!demand.validKeys.contains(template.key) || template.amount <= 0L) continue;
                long alreadyReserved = reserved.getOrDefault(template.key, 0L);
                long available = cache.getAvailableAmount(template.key);
                long availableForTask = Math.max(0L, available - alreadyReserved);
                long operations = Math.min(remainingOperations, availableForTask / template.amount);
                if (operations <= 0L) continue;
                long amount = operations * template.amount;
                reserved.merge(template.key, amount, AdaptiveBatchInventoryCacheBenchmark::saturatingAdd);
                remainingOperations -= operations;
            }
        }
        return reserved;
    }

    private static long benchmarkMany(List<Demand> demands, SyntheticInventory inventory, boolean cached) {
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) benchmarkOnce(demands, inventory, cached);
        return System.nanoTime() - start;
    }

    private static void benchmarkOnce(List<Demand> demands, SyntheticInventory inventory, boolean cached) {
        Map<String, Long> result = cached ? collectWithCache(demands, inventory) : collectWithoutCache(demands, inventory);
        blackhole ^= result.hashCode();
    }

    private static double median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record PossibleInput(String key, long amount) {}
    private record PossibleTemplate(String key, long amount) {}
    private record Demand(long operations, List<PossibleInput> possibleInputs, Set<String> validKeys) {}
    private record Metrics(long fuzzyScans, long fuzzyKeysVisited, long simulatedExtractions) {}

    private static final class SyntheticInventory {
        private final List<String> keys;
        private final Map<String, Long> amounts = new HashMap<>();
        private long fuzzyScans;
        private long fuzzyKeysVisited;
        private long simulatedExtractions;

        private SyntheticInventory(List<String> keys) { this.keys = keys; }

        private List<String> findFuzzyTemplates(String query) {
            this.fuzzyScans++;
            int group = groupOf(query);
            List<String> matching = new ArrayList<>(KEYS_PER_GROUP);
            for (String key : this.keys) {
                this.fuzzyKeysVisited++;
                if (groupOf(key) == group) matching.add(key);
            }
            return matching;
        }

        private long getAvailableAmount(String key) {
            this.simulatedExtractions++;
            return this.amounts.getOrDefault(key, 0L);
        }

        private Metrics metrics() {
            return new Metrics(this.fuzzyScans, this.fuzzyKeysVisited, this.simulatedExtractions);
        }

        private void resetMetrics() {
            this.fuzzyScans = 0L;
            this.fuzzyKeysVisited = 0L;
            this.simulatedExtractions = 0L;
        }

        private static int groupOf(String key) {
            int separator = key.indexOf('-', 4);
            return Integer.parseInt(key.substring(4, separator));
        }
    }
}
