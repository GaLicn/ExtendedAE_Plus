package com.extendedae_plus.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;
import com.wintercogs.ae2omnicells.common.init.OCItems;
import com.wintercogs.ae2omnicells.common.items.AEBigIntegerCellItem;
import com.wintercogs.ae2omnicells.common.me.biginteger.AEBigIntegerCellData;
import com.wintercogs.ae2omnicells.common.me.biginteger.AEBigIntegerCellInventory;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Compares the current devourer cell, Omni's BigInteger cell and Data Energistics' Trinity storage backend.
 *
 * <p>The Trinity implementation is intentionally called through its real storage backend rather than through a
 * fake StorageCell. Trinity storage is mounted into AE2 by the information exchange depot, while the other two
 * implementations are AE2 StorageCells. The report records this API boundary explicitly.</p>
 */
public final class TrinityStorageComparisonBenchmark {
    private static final int NBT_ITEM_TYPES = 4_096;
    private static final int ORDINARY_ITEM_TYPES = 4_096;
    private static final int STANDARD_ITEM_TYPES = NBT_ITEM_TYPES + ORDINARY_ITEM_TYPES;
    private static final int MILLION_KEY_TYPES = 1_000_000;
    private static final int DEFAULT_CONCURRENT_THREADS = 128;
    private static final int CONCURRENT_KEY_TYPES = 256;
    private static final int BIG_INTEGER_PERCENT = 5;
    private static final long SEED_AMOUNT = 1_000_000L;
    private static final long LONG_QUANTITY_AMOUNT = Long.MAX_VALUE / STANDARD_ITEM_TYPES;
    private static final BigInteger BIG_INTEGER_SEED = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
    private static final long CHECKSUM_MULTIPLIER = 0x5DEECE66DL;
    private static final String NBT_VARIANT_TAG = "benchmark_variant";
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();
    private static final ISaveProvider NOOP_SAVE_PROVIDER = () -> {
    };

    private final int warmupRounds;
    private final int measureRounds;
    private final int concurrentThreads;
    private final boolean millionFull;
    private final InfinityBigIntegerCellItem devourerItem;
    private final AEBigIntegerCellItem omniBigIntegerItem;
    private final Workload standardWorkload;
    private final Workload longWorkload;
    private final Workload millionWorkload;
    private final Workload concurrentWorkload;

    private TrinityStorageComparisonBenchmark(int warmupRounds, int measureRounds,
                                              int concurrentThreads, boolean millionFull) {
        this.warmupRounds = warmupRounds;
        this.measureRounds = measureRounds;
        this.concurrentThreads = concurrentThreads;
        this.millionFull = millionFull;

        if (ModItems.CELL_BENCHMARK_ORDINARY_ITEMS.size() != ORDINARY_ITEM_TYPES
                || ModItems.CELL_BENCHMARK_NBT_ITEM == null) {
            throw new IllegalStateException(
                    "Benchmark items are not registered. Start the Trinity comparison run with benchmark item registration enabled.");
        }
        this.devourerItem = requireItem(ModItems.INFINITY_BIGINTEGER_CELL_ITEM.get(),
                InfinityBigIntegerCellItem.class, "extendedae_plus:infinity_biginteger_cell");
        this.omniBigIntegerItem = requireItem(OCItems.CREATIVE_AE_CELL_BIGINTEGER.get(),
                AEBigIntegerCellItem.class, "ae2omnicells:creative_ae_cell_biginteger");
        this.standardWorkload = createStandardWorkload();
        this.longWorkload = new Workload(standardWorkload.keys, uniformAmounts(standardWorkload.keys.length,
                LONG_QUANTITY_AMOUNT));
        this.concurrentWorkload = createConcurrentWorkload(CONCURRENT_KEY_TYPES);
        this.millionWorkload = createMillionWorkload();
    }

    public static void run(MinecraftServer server) {
        Options options = Options.fromSystemProperties();
        new TrinityStorageComparisonBenchmark(options.warmupRounds(), options.measureRounds(),
                options.concurrentThreads(), options.millionFull()).execute(options.reportPath());
    }

    private static <T> T requireItem(Item item, Class<T> type, String id) {
        if (!type.isInstance(item)) {
            throw new IllegalStateException("Expected " + id + " to be " + type.getSimpleName()
                    + ", got " + item.getClass().getName());
        }
        return type.cast(item);
    }

    private void execute(Path reportPath) {
        List<Measurement> regularMeasurements = new ArrayList<>();
        List<ConcurrentMeasurement> concurrentMeasurements = new ArrayList<>();

        System.out.println();
        System.out.println("=== 吞噬盘 vs Omni BigInteger 盘 vs DE Trinity 存储三方性能对比 ===");
        System.out.printf(Locale.ROOT, "标准数据集: %d 个普通 key + %d 个 NBT key = %d 个 key%n",
                ORDINARY_ITEM_TYPES, NBT_ITEM_TYPES, STANDARD_ITEM_TYPES);
        System.out.printf(Locale.ROOT, "预热: %d 轮；正式测量: %d 轮；并发线程: %d%n",
                warmupRounds, measureRounds, concurrentThreads);
        System.out.println("计时范围: 输入/输出计 insert/extract；终端读取计 getAvailableStacks/addAvailableStacks。" +
                " 建盘、预置、线程池和校验不计入正式计时。");
        System.out.println("DE 组使用真实 TrinityDataCoreStorageSavedData，不把 Trinity 伪装成 StorageCell。");
        System.out.println();

        for (Scenario scenario : Scenario.values()) {
            if (scenario.millionOnly && !millionFull) {
                continue;
            }
            Measurement measurement = compare(scenario);
            regularMeasurements.add(measurement);
            printMeasurement(measurement);
        }

        for (ConcurrentScenario scenario : ConcurrentScenario.values()) {
            ConcurrentMeasurement measurement = compareConcurrent(scenario);
            concurrentMeasurements.add(measurement);
            printConcurrentMeasurement(measurement);
        }

        writeReport(reportPath, regularMeasurements, concurrentMeasurements);
        System.out.println("三方对比报告: " + reportPath.toAbsolutePath());
    }

    private Measurement compare(Scenario scenario) {
        for (int round = 0; round < warmupRounds; round++) {
            runThreeSamples(scenario, round);
        }

        long[] devourerSamples = new long[measureRounds];
        long[] omniSamples = new long[measureRounds];
        long[] trinitySamples = new long[measureRounds];
        Result expected = null;
        for (int round = 0; round < measureRounds; round++) {
            Sample[] samples = runThreeSamples(scenario, round + warmupRounds);
            if (!samples[0].result.sameState(samples[1].result)
                    || !samples[0].result.sameState(samples[2].result)) {
                throw new AssertionError(scenario.label + " 结果不一致: "
                        + samples[0].result + " != " + samples[1].result + " != " + samples[2].result);
            }
            if (expected == null) {
                expected = samples[0].result;
            } else if (!expected.sameState(samples[0].result)) {
                throw new AssertionError(scenario.label + " 各轮最终状态不稳定: "
                        + expected + " != " + samples[0].result);
            }
            devourerSamples[round] = samples[0].elapsedNanos;
            omniSamples[round] = samples[1].elapsedNanos;
            trinitySamples[round] = samples[2].elapsedNanos;
        }
        return new Measurement(scenario, average(devourerSamples), average(omniSamples),
                average(trinitySamples), expected);
    }

    private Sample[] runThreeSamples(Scenario scenario, int order) {
        Sample[] samples = new Sample[3];
        switch (Math.floorMod(order, 3)) {
            case 0 -> {
                samples[0] = runSample(scenario, Disk.DEVOURER);
                samples[1] = runSample(scenario, Disk.OMNI_BIGINTEGER);
                samples[2] = runSample(scenario, Disk.TRINITY);
            }
            case 1 -> {
                samples[2] = runSample(scenario, Disk.TRINITY);
                samples[0] = runSample(scenario, Disk.DEVOURER);
                samples[1] = runSample(scenario, Disk.OMNI_BIGINTEGER);
            }
            case 2 -> {
                samples[1] = runSample(scenario, Disk.OMNI_BIGINTEGER);
                samples[2] = runSample(scenario, Disk.TRINITY);
                samples[0] = runSample(scenario, Disk.DEVOURER);
            }
            default -> throw new AssertionError("Unexpected sample order");
        }
        return samples;
    }

    private Sample runSample(Scenario scenario, Disk disk) {
        Workload workload = workload(scenario.workloadKind);
        Backend backend = createBackend(disk);
        seed(backend, workload, scenario.seedKind);

        long start = System.nanoTime();
        long returned;
        if (scenario.operation == Operation.TERMINAL_READ) {
            backend.readAvailable();
            returned = 0;
        } else if (scenario.operation == Operation.INPUT_SIMULATE) {
            returned = runInput(backend, workload, Actionable.SIMULATE);
        } else if (scenario.operation == Operation.INPUT_MODULATE) {
            returned = runInput(backend, workload, Actionable.MODULATE);
        } else if (scenario.operation == Operation.OUTPUT_SIMULATE) {
            returned = runOutput(backend, workload, Actionable.SIMULATE);
        } else {
            returned = runOutput(backend, workload, Actionable.MODULATE);
        }
        long elapsed = System.nanoTime() - start;
        return new Sample(elapsed, backend.result(returned));
    }

    private Backend createBackend(Disk disk) {
        return switch (disk) {
            case DEVOURER -> {
                InfinityStorageManager manager = new InfinityStorageManager();
                StorageCell cell = InfinityBigIntegerCellInventory.createInventory(
                        new ItemStack(devourerItem), NOOP_SAVE_PROVIDER, manager);
                yield new CellBackend(cell);
            }
            case OMNI_BIGINTEGER -> {
                Object2ObjectOpenHashMap<AEKey, BigInteger> storage = new Object2ObjectOpenHashMap<>();
                AEBigIntegerCellData data = new AEBigIntegerCellData(storage);
                yield new CellBackend(new AEBigIntegerCellInventory(data,
                        new ItemStack(omniBigIntegerItem), omniBigIntegerItem, NOOP_SAVE_PROVIDER));
            }
            case TRINITY -> new TrinityBackend();
        };
    }

    private void seed(Backend backend, Workload workload, SeedKind seedKind) {
        if (seedKind == SeedKind.EMPTY) {
            return;
        }
        long amount = seedKind == SeedKind.LONG_QUANTITY ? LONG_QUANTITY_AMOUNT : SEED_AMOUNT;
        if (seedKind == SeedKind.BIGINTEGER) {
            for (AEKey key : workload.keys) {
                insertChecked(backend, key, Long.MAX_VALUE);
                insertChecked(backend, key, 1L);
            }
            return;
        }
        for (AEKey key : workload.keys) {
            insertChecked(backend, key, amount);
        }
    }

    private static void insertChecked(Backend backend, AEKey key, long amount) {
        long inserted = backend.insert(key, amount, Actionable.MODULATE);
        if (inserted != amount) {
            throw new AssertionError("预置失败: expected " + amount + ", got " + inserted);
        }
    }

    private static long runInput(Backend backend, Workload workload, Actionable mode) {
        long returned = 0;
        for (int i = 0; i < workload.keys.length; i++) {
            returned += backend.insert(workload.keys[i], workload.amounts[i], mode);
        }
        return returned;
    }

    private static long runOutput(Backend backend, Workload workload, Actionable mode) {
        long returned = 0;
        for (int i = 0; i < workload.keys.length; i++) {
            returned += backend.extract(workload.keys[i], workload.amounts[i], mode);
        }
        return returned;
    }

    private ConcurrentMeasurement compareConcurrent(ConcurrentScenario scenario) {
        for (int round = 0; round < warmupRounds; round++) {
            runThreeConcurrentSamples(scenario, round);
        }

        long[] devourerSamples = new long[measureRounds];
        long[] omniSamples = new long[measureRounds];
        long[] trinitySamples = new long[measureRounds];
        Result expected = null;
        for (int round = 0; round < measureRounds; round++) {
            ConcurrentSample[] samples = runThreeConcurrentSamples(scenario, round + warmupRounds);
            if (!samples[0].result.sameState(samples[1].result)
                    || !samples[0].result.sameState(samples[2].result)) {
                throw new AssertionError(scenario.label(concurrentThreads) + " 结果不一致: "
                        + samples[0].result + " != " + samples[1].result + " != " + samples[2].result);
            }
            if (expected == null) {
                expected = samples[0].result;
            } else if (!expected.sameState(samples[0].result)) {
                throw new AssertionError(scenario.label(concurrentThreads) + " 各轮最终状态不稳定");
            }
            devourerSamples[round] = samples[0].elapsedNanos;
            omniSamples[round] = samples[1].elapsedNanos;
            trinitySamples[round] = samples[2].elapsedNanos;
        }
        return new ConcurrentMeasurement(scenario, average(devourerSamples), average(omniSamples),
                average(trinitySamples), expected);
    }

    private ConcurrentSample[] runThreeConcurrentSamples(ConcurrentScenario scenario, int order) {
        ConcurrentSample[] samples = new ConcurrentSample[3];
        switch (Math.floorMod(order, 3)) {
            case 0 -> {
                samples[0] = runConcurrentSample(scenario, Disk.DEVOURER);
                samples[1] = runConcurrentSample(scenario, Disk.OMNI_BIGINTEGER);
                samples[2] = runConcurrentSample(scenario, Disk.TRINITY);
            }
            case 1 -> {
                samples[2] = runConcurrentSample(scenario, Disk.TRINITY);
                samples[0] = runConcurrentSample(scenario, Disk.DEVOURER);
                samples[1] = runConcurrentSample(scenario, Disk.OMNI_BIGINTEGER);
            }
            case 2 -> {
                samples[1] = runConcurrentSample(scenario, Disk.OMNI_BIGINTEGER);
                samples[2] = runConcurrentSample(scenario, Disk.TRINITY);
                samples[0] = runConcurrentSample(scenario, Disk.DEVOURER);
            }
            default -> throw new AssertionError("Unexpected concurrent sample order");
        }
        return samples;
    }

    private ConcurrentSample runConcurrentSample(ConcurrentScenario scenario, Disk disk) {
        int backendCount = scenario.group == ConcurrencyGroup.SHARED ? 1 : concurrentThreads;
        Backend[] backends = new Backend[backendCount];
        for (int i = 0; i < backendCount; i++) {
            backends[i] = createBackend(disk);
            if (scenario.operation == Operation.OUTPUT || scenario.quantityMode == QuantityMode.BIGINTEGER) {
                seed(backends[i], concurrentWorkload, SeedKind.STANDARD);
                if (scenario.quantityMode == QuantityMode.BIGINTEGER) {
                    promoteConcurrentBigIntegerKeys(backends[i]);
                }
            }
        }

        long[] returnedByThread = new long[concurrentThreads];
        CountDownLatch ready = new CountDownLatch(concurrentThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentThreads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        try {
            for (int threadIndex = 0; threadIndex < concurrentThreads; threadIndex++) {
                int index = threadIndex;
                executor.execute(() -> {
                    try {
                        ready.countDown();
                        await(ready, "concurrent workers ready");
                        start.await();
                        Backend backend = backends[scenario.group == ConcurrencyGroup.SHARED ? 0 : index];
                        long returned = 0;
                        for (int keyIndex = 0; keyIndex < concurrentWorkload.keys.length; keyIndex++) {
                            if (scenario.group == ConcurrencyGroup.SHARED) {
                                synchronized (backend) {
                                    returned += applyConcurrentOperation(scenario, backend, keyIndex);
                                }
                            } else {
                                returned += applyConcurrentOperation(scenario, backend, keyIndex);
                            }
                        }
                        returnedByThread[index] = returned;
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        done.countDown();
                    }
                });
            }

            await(ready, "concurrent workers ready");
            long startNanos = System.nanoTime();
            start.countDown();
            await(done, "concurrent workers done");
            long elapsed = System.nanoTime() - startNanos;
            if (!failures.isEmpty()) {
                throw new IllegalStateException("Concurrent benchmark failed", failures.get(0));
            }
            long returned = 0;
            for (long threadReturned : returnedByThread) {
                returned += threadReturned;
            }
            return new ConcurrentSample(elapsed, aggregateResult(backends, returned));
        } finally {
            executor.shutdownNow();
        }
    }

    private void promoteConcurrentBigIntegerKeys(Backend backend) {
        int bigCount = Math.max(1, concurrentWorkload.keys.length * BIG_INTEGER_PERCENT / 100);
        for (int i = 0; i < bigCount; i++) {
            insertChecked(backend, concurrentWorkload.keys[i], Long.MAX_VALUE);
            insertChecked(backend, concurrentWorkload.keys[i], 1L);
        }
    }

    private long applyConcurrentOperation(ConcurrentScenario scenario, Backend backend, int keyIndex) {
        AEKey key = concurrentWorkload.keys[keyIndex];
        long amount = concurrentWorkload.amounts[keyIndex];
        return scenario.operation == Operation.INPUT
                ? backend.insert(key, amount, Actionable.MODULATE)
                : backend.extract(key, amount, Actionable.MODULATE);
    }

    private Result aggregateResult(Backend[] backends, long returned) {
        KeyCounter available = new KeyCounter();
        for (Backend backend : backends) {
            KeyCounter backendContents = backend.readAvailable();
            for (var entry : backendContents) {
                addSaturating(available, entry.getKey(), entry.getLongValue());
            }
        }
        return summarize(available, returned);
    }

    private static void addSaturating(KeyCounter target, AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }

        long existing = target.get(key);
        if (existing == Long.MAX_VALUE) {
            return;
        }
        if (existing < 0 || existing > Long.MAX_VALUE - amount) {
            target.set(key, Long.MAX_VALUE);
        } else {
            target.add(key, amount);
        }
    }

    private static void await(CountDownLatch latch, String phase) {
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out waiting for " + phase);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + phase, exception);
        }
    }

    private Workload workload(WorkloadKind kind) {
        return kind == WorkloadKind.STANDARD ? standardWorkload
                : kind == WorkloadKind.LONG_QUANTITY ? longWorkload : millionWorkload;
    }

    private Workload createStandardWorkload() {
        AEKey[] keys = new AEKey[STANDARD_ITEM_TYPES];
        for (int i = 0; i < ORDINARY_ITEM_TYPES; i++) {
            keys[i] = AEItemKey.of(new ItemStack(ModItems.CELL_BENCHMARK_ORDINARY_ITEMS.get(i).get()));
        }
        Item nbtItem = ModItems.CELL_BENCHMARK_NBT_ITEM.get();
        for (int i = 0; i < NBT_ITEM_TYPES; i++) {
            ItemStack stack = new ItemStack(nbtItem);
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_VARIANT_TAG, i);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            keys[ORDINARY_ITEM_TYPES + i] = AEItemKey.of(stack);
        }
        return new Workload(keys, createAmounts(STANDARD_ITEM_TYPES, CHECKSUM_MULTIPLIER));
    }

    private Workload createConcurrentWorkload(int count) {
        AEKey[] keys = new AEKey[count];
        for (int i = 0; i < count; i++) {
            int sourceIndex = (int) ((long) i * standardWorkload.keys.length / count);
            keys[i] = standardWorkload.keys[sourceIndex];
        }
        return new Workload(keys, createAmounts(count, 0x2468ACE1L));
    }

    private Workload createMillionWorkload() {
        AEKey[] keys = new AEKey[MILLION_KEY_TYPES];
        Item nbtItem = ModItems.CELL_BENCHMARK_NBT_ITEM.get();
        for (int i = 0; i < MILLION_KEY_TYPES; i++) {
            ItemStack stack = new ItemStack(nbtItem);
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_VARIANT_TAG, i);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            keys[i] = AEItemKey.of(stack);
        }
        return new Workload(keys, uniformAmounts(MILLION_KEY_TYPES, 1L));
    }

    private static long[] createAmounts(int count, long initialValue) {
        long[] result = new long[count];
        long value = initialValue;
        for (int i = 0; i < count; i++) {
            value = value * 2_862_933_555_777_941_757L + 3_039_700_493L;
            result[i] = 1L + Long.remainderUnsigned(value >>> 17, 1_024L);
        }
        return result;
    }

    private static long[] uniformAmounts(int count, long amount) {
        long[] amounts = new long[count];
        java.util.Arrays.fill(amounts, amount);
        return amounts;
    }

    private static Result summarize(KeyCounter available, long returned) {
        long checksum = 0;
        BigInteger total = BigInteger.ZERO;
        int count = 0;
        for (var entry : available) {
            checksum += entry.getKey().hashCode() * CHECKSUM_MULTIPLIER + entry.getLongValue();
            total = total.add(BigInteger.valueOf(entry.getLongValue()));
            count++;
        }
        return new Result(returned, total, count, checksum, count == 0 ? "EMPTY" : "NOT_EMPTY");
    }

    private static long average(long[] samples) {
        double total = 0;
        for (long sample : samples) {
            total += sample;
        }
        return Math.max(1L, Math.round(total / samples.length));
    }

    private static double ratio(long referenceNanos, long testedNanos) {
        return (double) referenceNanos / testedNanos;
    }

    private static double throughput(long operations, long nanos) {
        return operations * 1_000_000_000D / nanos;
    }

    private static String formatDuration(long nanos) {
        if (nanos < 1_000) return String.format(Locale.ROOT, "%,d ns", nanos);
        if (nanos < 1_000_000) return String.format(Locale.ROOT, "%.2f us", nanos / 1_000D);
        if (nanos < 1_000_000_000) return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000D);
        return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000D);
    }

    private void printMeasurement(Measurement measurement) {
        long operations = measurement.scenario.operationCount();
        System.out.printf(Locale.ROOT, "[%s] 吞噬盘 %s；Omni BigInteger %s；DE Trinity %s；吞噬盘/Omni %.2fx；吞噬盘/DE %.2fx%n",
                measurement.scenario.label, formatDuration(measurement.devourerNanos),
                formatDuration(measurement.omniNanos), formatDuration(measurement.trinityNanos),
                ratio(measurement.omniNanos, measurement.devourerNanos),
                ratio(measurement.trinityNanos, measurement.devourerNanos));
        System.out.printf(Locale.ROOT, "  吞吐量: 吞噬盘 %,.0f；Omni %,.0f；DE %,.0f 次/秒%n",
                throughput(operations, measurement.devourerNanos),
                throughput(operations, measurement.omniNanos), throughput(operations, measurement.trinityNanos));
    }

    private void printConcurrentMeasurement(ConcurrentMeasurement measurement) {
        long operations = (long) concurrentThreads * concurrentWorkload.keys.length;
        System.out.printf(Locale.ROOT, "[%s] 吞噬盘 %s；Omni BigInteger %s；DE Trinity %s；吞噬盘/Omni %.2fx；吞噬盘/DE %.2fx%n",
                measurement.scenario.label(concurrentThreads), formatDuration(measurement.devourerNanos),
                formatDuration(measurement.omniNanos), formatDuration(measurement.trinityNanos),
                ratio(measurement.omniNanos, measurement.devourerNanos),
                ratio(measurement.trinityNanos, measurement.devourerNanos));
        System.out.printf(Locale.ROOT, "  吞吐量: 吞噬盘 %,.0f；Omni %,.0f；DE %,.0f 次/秒%n",
                throughput(operations, measurement.devourerNanos),
                throughput(operations, measurement.omniNanos), throughput(operations, measurement.trinityNanos));
    }

    private void writeReport(Path reportPath, List<Measurement> measurements,
                             List<ConcurrentMeasurement> concurrentMeasurements) {
        StringBuilder report = new StringBuilder();
        report.append("# 吞噬盘、Omni BigInteger 盘与 DE Trinity 三方性能对比报告\n\n");
        report.append("## 测试对象与可比性边界\n\n");
        report.append("- 吞噬盘：当前 `InfinityBigIntegerCellInventory`，通过真实 AE2 `StorageCell` 接口调用。\n");
        report.append("- Omni：`AEBigIntegerCellInventory`，来自 `ae2omnicells`，通过真实 AE2 `StorageCell` 接口调用。\n");
        report.append("- DE：Data Energistics 的 `TrinityDataCoreStorageSavedData`，即三位一体存储核心的真实 BigInteger 后端；它不是 `StorageCell`，而是由三位一体信息交换仓挂载到 AE 网络。\n");
        report.append("- 因此本报告比较的是三者的共同存储操作热路径；DE 组不包含多方块建造、网络挂载、世界存档和交换仓生命周期成本。\n");
        report.append("- 本次不使用 DE 的 `InfiniteDataCellInventory`，因为它是只接受 DE 自定义 key 的另一种无限盘，不能代表 Trinity 存储核心。\n\n");
        report.append("## 固定变量与测量方法\n\n");
        report.append("- 数据集：同一批 4,096 个普通物品 key + 4,096 个带 `CUSTOM_DATA.benchmark_variant` 的 NBT key，共 8,192 个 key；三方按相同顺序接收。\n");
        report.append("- 普通数量：每个预置 key 为 ").append(SEED_AMOUNT).append("；请求数量为确定性生成的 1..1024。\n");
        report.append("- BigInteger 数量：每个 key 在计时前预置 `Long.MAX_VALUE + 1`，通过 `Long.MAX_VALUE` 加 `1` 两次真实输入建立。\n");
        report.append("- long 数量级：每个 key 为 ").append(LONG_QUANTITY_AMOUNT).append("，约等于 `Long.MAX_VALUE / 8,192`，总量不溢出 long。\n");
        report.append("- 预热 ").append(warmupRounds).append(" 轮，正式测量 ").append(measureRounds)
                .append(" 轮；报告使用算术平均值。三方每轮轮换执行顺序，降低 JIT、缓存和 GC 顺序偏差。\n");
        report.append("- 输入/输出只计批量 `insert`/`extract`；终端读取只计 `getAvailableStacks` 或 DE 的 `addAvailableStacks`。建盘、预置、线程池、同步等待、结果汇总和校验不计时。\n");
        report.append("- 并发场景默认 ").append(concurrentThreads).append(" 个线程，每线程操作 ")
                .append(CONCURRENT_KEY_TYPES).append(" 个 key；共享单盘场景在每次操作外加锁，多盘场景每线程使用独立后端。\n\n");
        report.append("## 倍率口径\n\n");
        report.append("- `吞噬盘/Omni` = Omni 平均耗时 / 吞噬盘平均耗时；大于 1 表示吞噬盘更快，例如 `1.50x` 表示吞噬盘速度约为 Omni 的 1.50 倍。\n");
        report.append("- `吞噬盘/DE` = DE Trinity 平均耗时 / 吞噬盘平均耗时；大于 1 表示吞噬盘更快。\n");
        report.append("- `Omni/DE` = DE Trinity 平均耗时 / Omni 平均耗时；大于 1 表示 Omni 更快。\n\n");

        appendScenarioDescription(report);
        report.append("## 分组结果\n\n");
        appendMeasurementTable(report, measurements, WorkloadKind.STANDARD, "一、标准 8,192 key");
        appendMeasurementTable(report, measurements, WorkloadKind.LONG_QUANTITY, "二、long 数量级");
        appendMeasurementTable(report, measurements, WorkloadKind.MILLION, "三、百万 key");
        appendBigIntegerTable(report, measurements, "四、BigInteger 预置数量");
        appendConcurrentTable(report, concurrentMeasurements, ConcurrencyGroup.SHARED, "五、单盘共享并发");
        appendConcurrentTable(report, concurrentMeasurements, ConcurrencyGroup.INDEPENDENT, "六、多盘独立并发");

        report.append("## 总合并对比表\n\n");
        appendCombinedTable(report, measurements, concurrentMeasurements);
        report.append("## 结果校验与限制\n\n");
        report.append("每个场景每轮都比较三方的返回数量、终端可见总量、key 数、校验和与空/非空状态；任何不一致或异常都会终止测试。\n");
        report.append("DE Trinity 通过共同的 long `insert`/`extract` API 操作，内部以 BigInteger 保存；它不接受超过单次 AE2 long API 上限的直接调用，因此 BigInteger 场景通过计时前的两次输入建立超 long 库存，再测共同的 long 操作。\n");
        report.append("本测试是同一 JVM 内的工程基准，不是完全隔离 CPU、操作系统调度和 GC 的实验室统计实验；10 轮平均值用于同期工程对比，不能替代中位数、标准差和 P95 分析。\n\n");
        report.append("## 运行说明\n\n");
        report.append("`gradlew.bat runTrinityStorageComparisonBenchmark --no-daemon --no-configuration-cache --offline`\n\n");
        report.append("默认包含标准、long 数量级、BigInteger 预置、128 线程并发以及百万 key 终端读取；完整执行百万 key 输入/输出时追加 `-PtrinityBenchmarkMillionFull=true`。\n");

        try {
            Path parent = reportPath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(reportPath, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Trinity comparison report", exception);
        }
    }

    private void appendScenarioDescription(StringBuilder report) {
        report.append("## 测试用例说明\n\n");
        report.append("### 一、标准数据集\n\n");
        report.append("| 用例 | 数据集 | 预置 | 操作 | 是否修改 | 目的 |\n|---|---|---|---|---|---|\n");
        report.append("|输入 SIMULATE|8,192 key|空盘|逐 key insert|否|比较三方模拟输入路径|\n");
        report.append("|输入 MODULATE|8,192 key|空盘|逐 key insert|是|比较三方真实输入与 key 建立|\n");
        report.append("|输出 SIMULATE|8,192 key|每 key 1,000,000|逐 key extract|否|比较三方模拟输出|\n");
        report.append("|输出 MODULATE|8,192 key|每 key 1,000,000|逐 key extract|是|比较三方真实输出|\n");
        report.append("|终端读取|8,192 key|每 key 1,000,000|读取可用 key|否|比较网络列表刷新核心路径|\n\n");
        report.append("### 二、long 与 BigInteger 数量\n\n");
        report.append("| 用例 | 数量状态 | 操作 | 目的 |\n|---|---|---|---|\n");
        report.append("|long 数量级输入/输出/读取|每 key ").append(LONG_QUANTITY_AMOUNT)
                .append("|insert、extract、列表读取|比较接近 long 上限时的路径|\n");
        report.append("|BigInteger 预置后输入|每 key `Long.MAX_VALUE + 1`|输入 MODULATE|比较超 long 库存继续输入|\n");
        report.append("|BigInteger 预置后模拟输出|每 key `Long.MAX_VALUE + 1`|输出 SIMULATE|比较超 long 库存只读输出|\n");
        report.append("|BigInteger 预置后真实输出|每 key `Long.MAX_VALUE + 1`|输出 MODULATE|比较超 long 库存扣减|\n");
        report.append("|BigInteger 终端读取|每 key `Long.MAX_VALUE + 1`|列表读取|比较超 long 数量饱和到 AE2 long 的读取|\n\n");
        report.append("### 三、并发数据集\n\n");
        report.append("| 用例 | 线程/盘 | 数量状态 | 目的 |\n|---|---:|---|---|\n");
        report.append("|一个盘接受并发输入|").append(concurrentThreads).append(" 线程共享 1 盘|仅 long|观察共享盘输入锁竞争|\n");
        report.append("|一个盘处理并发输出|").append(concurrentThreads).append(" 线程共享 1 盘|仅 long|观察共享盘输出锁竞争|\n");
        report.append("|单盘预置少量 BigInteger 后输入|").append(concurrentThreads).append(" 线程共享 1 盘|5% key 为 BigInteger|观察混合数量输入|\n");
        report.append("|单盘预置少量 BigInteger 后输出|").append(concurrentThreads).append(" 线程共享 1 盘|5% key 为 BigInteger|观察混合数量输出|\n");
        report.append("|多个独立盘高并发输入|").append(concurrentThreads).append(" 线程各 1 盘|仅 long|观察无共享 Map 时输入吞吐|\n");
        report.append("|多个独立盘高并发输出|").append(concurrentThreads).append(" 线程各 1 盘|仅 long|观察无共享 Map 时输出吞吐|\n");
        report.append("|多个独立盘混合数量输入/输出|").append(concurrentThreads).append(" 线程各 1 盘|5% key 为 BigInteger|观察独立盘混合数量吞吐|\n\n");
    }

    private void appendMeasurementTable(StringBuilder report, List<Measurement> measurements,
                                        WorkloadKind kind, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 用例 | 操作数 | 吞噬盘平均 | Omni BigInteger 平均 | DE Trinity 平均 | 吞噬盘/Omni | 吞噬盘/DE | Omni/DE | 吞噬盘次/秒 | Omni次/秒 | DE次/秒 | 结果 key |\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Measurement measurement : measurements) {
            if (measurement.scenario.workloadKind != kind || measurement.scenario.bigIntegerOnly) continue;
            appendMeasurementRow(report, measurement.scenario.label, measurement.scenario.operationCount(),
                    measurement.devourerNanos, measurement.omniNanos, measurement.trinityNanos, measurement.result);
        }
        report.append('\n');
    }

    private void appendBigIntegerTable(StringBuilder report, List<Measurement> measurements, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 用例 | 操作数 | 吞噬盘平均 | Omni BigInteger 平均 | DE Trinity 平均 | 吞噬盘/Omni | 吞噬盘/DE | Omni/DE | 吞噬盘次/秒 | Omni次/秒 | DE次/秒 | 结果 key |\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Measurement measurement : measurements) {
            if (!measurement.scenario.bigIntegerOnly) continue;
            appendMeasurementRow(report, measurement.scenario.label, measurement.scenario.operationCount(),
                    measurement.devourerNanos, measurement.omniNanos, measurement.trinityNanos, measurement.result);
        }
        report.append('\n');
    }

    private void appendMeasurementRow(StringBuilder report, String label, long operations,
                                      long devourer, long omni, long trinity, Result result) {
        report.append('|').append(label).append('|').append(operations).append('|')
                .append(formatDuration(devourer)).append('|').append(formatDuration(omni)).append('|')
                .append(formatDuration(trinity)).append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(omni, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, omni))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, devourer))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, omni))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, trinity))).append('|')
                .append(result.itemCount).append('|').append('\n');
    }

    private void appendConcurrentTable(StringBuilder report, List<ConcurrentMeasurement> measurements,
                                       ConcurrencyGroup group, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 用例 | 线程数 | 总操作数 | 吞噬盘平均 | Omni BigInteger 平均 | DE Trinity 平均 | 吞噬盘/Omni | 吞噬盘/DE | Omni/DE | 吞噬盘次/秒 | Omni次/秒 | DE次/秒 | 结果 key |\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ConcurrentMeasurement measurement : measurements) {
            if (measurement.scenario.group != group) continue;
            appendConcurrentRow(report, measurement.scenario.label(concurrentThreads), concurrentThreads,
                    concurrentOperationCount(),
                    measurement.devourerNanos, measurement.omniNanos, measurement.trinityNanos, measurement.result);
        }
        report.append('\n');
    }

    private void appendConcurrentRow(StringBuilder report, String label, int threads, long operations,
                                     long devourer, long omni, long trinity, Result result) {
        report.append('|').append(label).append('|').append(threads).append('|').append(operations).append('|')
                .append(formatDuration(devourer)).append('|').append(formatDuration(omni)).append('|')
                .append(formatDuration(trinity)).append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(omni, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, omni))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, devourer))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, omni))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operations, trinity))).append('|')
                .append(result.itemCount).append('|').append('\n');
    }

    private void appendCombinedTable(StringBuilder report, List<Measurement> measurements,
                                     List<ConcurrentMeasurement> concurrentMeasurements) {
        report.append("| 分组 | 用例 | 操作数 | 吞噬盘平均 | Omni BigInteger 平均 | DE Trinity 平均 | 吞噬盘/Omni | 吞噬盘/DE | Omni/DE | 结果 key |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Measurement measurement : measurements) {
            String group = measurement.scenario.bigIntegerOnly ? "BigInteger 预置"
                    : measurement.scenario.workloadKind == WorkloadKind.STANDARD ? "标准 8,192 key"
                    : measurement.scenario.workloadKind == WorkloadKind.LONG_QUANTITY ? "long 数量级" : "百万 key";
            appendCombinedRow(report, group, measurement.scenario.label, measurement.scenario.operationCount(),
                    measurement.devourerNanos, measurement.omniNanos, measurement.trinityNanos, measurement.result);
        }
        for (ConcurrentMeasurement measurement : concurrentMeasurements) {
            appendCombinedRow(report, measurement.scenario.group.label, measurement.scenario.label(concurrentThreads),
                    concurrentOperationCount(), measurement.devourerNanos, measurement.omniNanos,
                    measurement.trinityNanos, measurement.result);
        }
        report.append('\n');
    }

    private static void appendCombinedRow(StringBuilder report, String group, String label, long operations,
                                          long devourer, long omni, long trinity, Result result) {
        report.append('|').append(group).append('|').append(label).append('|').append(operations).append('|')
                .append(formatDuration(devourer)).append('|').append(formatDuration(omni)).append('|')
                .append(formatDuration(trinity)).append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(omni, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, devourer)))
                .append('|').append(String.format(Locale.ROOT, "%.2fx", ratio(trinity, omni))).append('|')
                .append(result.itemCount).append('|').append('\n');
    }

    private long concurrentOperationCount() {
        return (long) concurrentThreads * concurrentWorkload.keys.length;
    }

    private enum Disk {
        DEVOURER,
        OMNI_BIGINTEGER,
        TRINITY
    }

    private enum WorkloadKind {
        STANDARD,
        LONG_QUANTITY,
        MILLION
    }

    private enum Operation {
        INPUT_SIMULATE,
        INPUT_MODULATE,
        OUTPUT_SIMULATE,
        OUTPUT_MODULATE,
        TERMINAL_READ,
        INPUT,
        OUTPUT
    }

    private enum SeedKind {
        EMPTY,
        STANDARD,
        LONG_QUANTITY,
        BIGINTEGER
    }

    private enum QuantityMode {
        LONG_ONLY,
        BIGINTEGER
    }

    private enum ConcurrencyGroup {
        SHARED("单盘共享"),
        INDEPENDENT("多盘独立");

        private final String label;

        ConcurrencyGroup(String label) {
            this.label = label;
        }
    }

    private enum Scenario {
        INPUT_SIMULATE("输入 SIMULATE", WorkloadKind.STANDARD, Operation.INPUT_SIMULATE, SeedKind.EMPTY, false, false),
        INPUT_MODULATE("输入 MODULATE", WorkloadKind.STANDARD, Operation.INPUT_MODULATE, SeedKind.EMPTY, false, false),
        OUTPUT_SIMULATE("输出 SIMULATE", WorkloadKind.STANDARD, Operation.OUTPUT_SIMULATE, SeedKind.STANDARD, false, false),
        OUTPUT_MODULATE("输出 MODULATE", WorkloadKind.STANDARD, Operation.OUTPUT_MODULATE, SeedKind.STANDARD, false, false),
        TERMINAL_READ("AE 终端读取", WorkloadKind.STANDARD, Operation.TERMINAL_READ, SeedKind.STANDARD, false, false),
        LONG_INPUT_SIMULATE("long 数量输入 SIMULATE", WorkloadKind.LONG_QUANTITY, Operation.INPUT_SIMULATE, SeedKind.EMPTY, false, false),
        LONG_INPUT_MODULATE("long 数量输入 MODULATE", WorkloadKind.LONG_QUANTITY, Operation.INPUT_MODULATE, SeedKind.EMPTY, false, false),
        LONG_OUTPUT_SIMULATE("long 数量输出 SIMULATE", WorkloadKind.LONG_QUANTITY, Operation.OUTPUT_SIMULATE, SeedKind.LONG_QUANTITY, false, false),
        LONG_OUTPUT_MODULATE("long 数量输出 MODULATE", WorkloadKind.LONG_QUANTITY, Operation.OUTPUT_MODULATE, SeedKind.LONG_QUANTITY, false, false),
        LONG_TERMINAL_READ("long 数量终端读取", WorkloadKind.LONG_QUANTITY, Operation.TERMINAL_READ, SeedKind.LONG_QUANTITY, false, false),
        BIG_INPUT("BigInteger 预置后继续输入", WorkloadKind.STANDARD, Operation.INPUT_MODULATE, SeedKind.BIGINTEGER, true, false),
        BIG_OUTPUT_SIMULATE("BigInteger 预置后模拟输出", WorkloadKind.STANDARD, Operation.OUTPUT_SIMULATE, SeedKind.BIGINTEGER, true, false),
        BIG_OUTPUT_MODULATE("BigInteger 预置后真实输出", WorkloadKind.STANDARD, Operation.OUTPUT_MODULATE, SeedKind.BIGINTEGER, true, false),
        BIG_TERMINAL_READ("BigInteger 预置后终端读取", WorkloadKind.STANDARD, Operation.TERMINAL_READ, SeedKind.BIGINTEGER, true, false),
        MILLION_TERMINAL_READ("百万 key AE 终端读取", WorkloadKind.MILLION, Operation.TERMINAL_READ, SeedKind.STANDARD, false, false),
        MILLION_INPUT_SIMULATE("百万 key 输入 SIMULATE", WorkloadKind.MILLION, Operation.INPUT_SIMULATE, SeedKind.EMPTY, false, true),
        MILLION_INPUT_MODULATE("百万 key 输入 MODULATE", WorkloadKind.MILLION, Operation.INPUT_MODULATE, SeedKind.EMPTY, false, true),
        MILLION_OUTPUT_SIMULATE("百万 key 输出 SIMULATE", WorkloadKind.MILLION, Operation.OUTPUT_SIMULATE, SeedKind.STANDARD, false, true),
        MILLION_OUTPUT_MODULATE("百万 key 输出 MODULATE", WorkloadKind.MILLION, Operation.OUTPUT_MODULATE, SeedKind.STANDARD, false, true);

        private final String label;
        private final WorkloadKind workloadKind;
        private final Operation operation;
        private final SeedKind seedKind;
        private final boolean bigIntegerOnly;
        private final boolean millionOnly;

        Scenario(String label, WorkloadKind workloadKind, Operation operation, SeedKind seedKind,
                 boolean bigIntegerOnly, boolean millionOnly) {
            this.label = label;
            this.workloadKind = workloadKind;
            this.operation = operation;
            this.seedKind = seedKind;
            this.bigIntegerOnly = bigIntegerOnly;
            this.millionOnly = millionOnly;
        }

        private long operationCount() {
            return workloadKind == WorkloadKind.MILLION ? MILLION_KEY_TYPES : STANDARD_ITEM_TYPES;
        }
    }

    private enum ConcurrentScenario {
        SHARED_INPUT_LONG("单盘共享 %d 线程输入（仅 long）", ConcurrencyGroup.SHARED, Operation.INPUT, QuantityMode.LONG_ONLY),
        SHARED_OUTPUT_LONG("单盘共享 %d 线程输出（仅 long）", ConcurrencyGroup.SHARED, Operation.OUTPUT, QuantityMode.LONG_ONLY),
        SHARED_INPUT_BIG("单盘共享 %d 线程输入（少量 BigInteger）", ConcurrencyGroup.SHARED, Operation.INPUT, QuantityMode.BIGINTEGER),
        SHARED_OUTPUT_BIG("单盘共享 %d 线程输出（少量 BigInteger）", ConcurrencyGroup.SHARED, Operation.OUTPUT, QuantityMode.BIGINTEGER),
        INDEPENDENT_INPUT_LONG("多盘独立 %d 线程输入（仅 long）", ConcurrencyGroup.INDEPENDENT, Operation.INPUT, QuantityMode.LONG_ONLY),
        INDEPENDENT_OUTPUT_LONG("多盘独立 %d 线程输出（仅 long）", ConcurrencyGroup.INDEPENDENT, Operation.OUTPUT, QuantityMode.LONG_ONLY),
        INDEPENDENT_INPUT_BIG("多盘独立 %d 线程输入（少量 BigInteger）", ConcurrencyGroup.INDEPENDENT, Operation.INPUT, QuantityMode.BIGINTEGER),
        INDEPENDENT_OUTPUT_BIG("多盘独立 %d 线程输出（少量 BigInteger）", ConcurrencyGroup.INDEPENDENT, Operation.OUTPUT, QuantityMode.BIGINTEGER);

        private final String labelTemplate;
        private final ConcurrencyGroup group;
        private final Operation operation;
        private final QuantityMode quantityMode;

        ConcurrentScenario(String labelTemplate, ConcurrencyGroup group, Operation operation, QuantityMode quantityMode) {
            this.labelTemplate = labelTemplate;
            this.group = group;
            this.operation = operation;
            this.quantityMode = quantityMode;
        }

        private String label(int threads) {
            return labelTemplate.formatted(threads);
        }
    }

    private interface Backend {
        long insert(AEKey key, long amount, Actionable mode);

        long extract(AEKey key, long amount, Actionable mode);

        void readAvailable(KeyCounter out);

        default KeyCounter readAvailable() {
            KeyCounter out = new KeyCounter();
            readAvailable(out);
            return out;
        }

        default Result result(long returned) {
            return summarize(readAvailable(), returned);
        }
    }

    private static final class CellBackend implements Backend {
        private final StorageCell cell;

        private CellBackend(StorageCell cell) {
            this.cell = cell;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            return cell.insert(key, amount, mode, ACTION_SOURCE);
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return cell.extract(key, amount, mode, ACTION_SOURCE);
        }

        @Override
        public void readAvailable(KeyCounter out) {
            cell.getAvailableStacks(out);
        }
    }

    private static final class TrinityBackend implements Backend {
        private final TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        private final UUID hostId = UUID.randomUUID();

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            return storage.insert(hostId, key, amount, mode, TrinityDataCoreStorageProfile.UNLIMITED);
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return storage.extract(hostId, key, amount, mode);
        }

        @Override
        public void readAvailable(KeyCounter out) {
            storage.addAvailableStacks(hostId, out);
        }
    }

    private record Workload(AEKey[] keys, long[] amounts) {
    }

    private record Sample(long elapsedNanos, Result result) {
    }

    private record ConcurrentSample(long elapsedNanos, Result result) {
    }

    private record Measurement(Scenario scenario, long devourerNanos, long omniNanos,
                               long trinityNanos, Result result) {
    }

    private record ConcurrentMeasurement(ConcurrentScenario scenario, long devourerNanos,
                                         long omniNanos, long trinityNanos, Result result) {
    }

    private record Result(long returned, BigInteger visibleTotal, int itemCount,
                          long checksum, String status) {
        private boolean sameState(Result other) {
            return returned == other.returned && visibleTotal.equals(other.visibleTotal)
                    && itemCount == other.itemCount && checksum == other.checksum
                    && status.equals(other.status);
        }
    }

    private record Options(int warmupRounds, int measureRounds, int concurrentThreads,
                           boolean millionFull, Path reportPath) {
        private static Options fromSystemProperties() {
            int warmup = positive("warmup", Integer.getInteger("extendedae_plus.trinity_benchmark.warmup", 2));
            int measure = positive("measure", Integer.getInteger("extendedae_plus.trinity_benchmark.measure", 10));
            int threads = positive("concurrentThreads", Integer.getInteger(
                    "extendedae_plus.trinity_benchmark.concurrent_threads", DEFAULT_CONCURRENT_THREADS));
            boolean millionFull = Boolean.getBoolean("extendedae_plus.trinity_benchmark.million_full");
            String configuredPath = System.getProperty("extendedae_plus.trinity_benchmark.report");
            Path report = configuredPath == null || configuredPath.isBlank()
                    ? Path.of("build", "reports", "trinity-storage-comparison",
                    "trinity-storage-comparison.md") : Path.of(configuredPath);
            return new Options(warmup, measure, threads, millionFull, report);
        }

        private static int positive(String name, int value) {
            if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
            return value;
        }
    }
}
