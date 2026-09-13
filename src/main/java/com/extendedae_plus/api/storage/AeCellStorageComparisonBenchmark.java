package com.extendedae_plus.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import com.wintercogs.ae2omnicells.common.init.OCItems;
import com.wintercogs.ae2omnicells.common.items.AEBigIntegerCellItem;
import com.wintercogs.ae2omnicells.common.items.AEUniversalCellItem;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory;
import com.wintercogs.ae2omnicells.common.me.biginteger.AEBigIntegerCellData;
import com.wintercogs.ae2omnicells.common.me.biginteger.AEBigIntegerCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
 * Runs the same StorageCell workload against the ExtendedAE devourer cells and Omni Cells' long/BigInteger cells.
 *
 * <p>This class deliberately runs from the NeoForge server lifecycle. ItemStack construction requires the
 * Minecraft registry bootstrap to have completed, while the devourer cell requires a real modded runtime.</p>
 */
public final class AeCellStorageComparisonBenchmark {
    private static final int NBT_ITEM_TYPES = 4_096;
    private static final int ORDINARY_ITEM_TYPES = 4_096;
    private static final int STANDARD_ITEM_TYPES = NBT_ITEM_TYPES + ORDINARY_ITEM_TYPES;
    private static final int MILLION_KEY_TYPES = 1_000_000;
    private static final int DEFAULT_CONCURRENT_THREADS = 128;
    private static final int DEFAULT_CONCURRENT_KEY_TYPES = 256;
    private static final int CONCURRENT_BIG_PERCENT = 5;
    private static final long SEED_AMOUNT = 1_000_000L;
    // 让 8,192 个 key 的总量仍不超过 Long.MAX_VALUE，同时让每个 key 真正处于 long 数量级。
    private static final long LONG_QUANTITY_AMOUNT = Long.MAX_VALUE / STANDARD_ITEM_TYPES;
    private static final long CHECKSUM_MULTIPLIER = 0x5DEECE66DL;
    private static final String NBT_VARIANT_TAG = "benchmark_variant";
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();
    private static final ISaveProvider NOOP_SAVE_PROVIDER = () -> {
    };

    private final int warmupRounds;
    private final int measureRounds;
    private final Workload standardWorkload;
    private final Workload longWorkload;
    private final Workload millionWorkload;
    private final Workload concurrentWorkload;
    private final int concurrentThreads;
    private final boolean millionFull;
    private final InfinityBigIntegerCellItem devourerItem;
    private final AEBigIntegerCellItem omniItem;
    private final AEUniversalCellItem omniLongItem;

    private AeCellStorageComparisonBenchmark(int warmupRounds, int measureRounds,
                                             int concurrentThreads, boolean millionFull) {
        this.warmupRounds = warmupRounds;
        this.measureRounds = measureRounds;
        this.concurrentThreads = concurrentThreads;
        this.millionFull = millionFull;

        if (ModItems.CELL_BENCHMARK_ORDINARY_ITEMS.size() != ORDINARY_ITEM_TYPES
                || ModItems.CELL_BENCHMARK_NBT_ITEM == null) {
            throw new IllegalStateException(
                    "Cell benchmark items are not registered. Start the dedicated cell benchmark server run.");
        }

        this.devourerItem = requireItem(ModItems.INFINITY_BIGINTEGER_CELL_ITEM.get(), InfinityBigIntegerCellItem.class,
                "extendedae_plus:infinity_biginteger_cell");
        this.omniItem = requireItem(OCItems.CREATIVE_AE_CELL_BIGINTEGER.get(), AEBigIntegerCellItem.class,
                "ae2omnicells:creative_ae_cell_biginteger");
        this.omniLongItem = requireItem(OCItems.CREATIVE_AE_CELL_LONG.get(), AEUniversalCellItem.class,
                "ae2omnicells:creative_ae_cell_long");
        this.standardWorkload = createStandardWorkload();
        // long 数量级用例使用同一批 key，但每个 key 的数量接近 Long.MAX_VALUE / 8,192。
        this.longWorkload = createLongWorkload();
        this.concurrentWorkload = createConcurrentWorkload(DEFAULT_CONCURRENT_KEY_TYPES);
        this.millionWorkload = createMillionWorkload();
    }

    public static void run(MinecraftServer server) {
        Options options = Options.fromSystemProperties();
        AeCellStorageComparisonBenchmark benchmark =
                new AeCellStorageComparisonBenchmark(
                        options.warmupRounds(), options.measureRounds(),
                        options.concurrentThreads(), options.millionFull());
        benchmark.execute(options.reportPath());
    }

    private static <T> T requireItem(Item item, Class<T> type, String id) {
        if (!type.isInstance(item)) {
            throw new IllegalStateException("Expected " + id + " to be " + type.getSimpleName()
                    + ", got " + item.getClass().getName());
        }
        return type.cast(item);
    }

    private void execute(Path reportPath) {
        List<Measurement> measurements = new ArrayList<>(Scenario.values().length);
        List<LongMeasurement> longMeasurements = new ArrayList<>(LongScenario.values().length);
        List<ConcurrentMeasurement> concurrentMeasurements =
                new ArrayList<>(ConcurrentScenario.values().length);

        System.out.println();
        System.out.println("=== 优化后吞噬盘 vs 未优化吞噬盘 vs Omni Cells 无限盘同期对比测试 ===");
        System.out.printf(Locale.ROOT, "标准数据集: %d 个普通 key + %d 个 NBT key = %d 个 key%n",
                ORDINARY_ITEM_TYPES, NBT_ITEM_TYPES, STANDARD_ITEM_TYPES);
        System.out.printf(Locale.ROOT, "百万数据集: %d 个不同 NBT key；预热: %d 轮；测量: %d 轮%n",
                MILLION_KEY_TYPES, warmupRounds, measureRounds);
        System.out.println("计时范围: 输入/输出只计 insert/extract；终端读取只计 getAvailableStacks。");
        System.out.println("百万 key 默认执行终端读取；百万 key 完整输入输出: " + (millionFull ? "开启" : "关闭"));
        System.out.println();

        for (Scenario scenario : Scenario.values()) {
            if (scenario.millionOnly && !millionFull) {
                continue;
            }
            Measurement measurement = compare(scenario);
            measurements.add(measurement);
            printMeasurement(measurement);
        }

        System.out.println("=== long 数量级：优化后吞噬盘 vs 未优化吞噬盘 vs Omni long 盘 ===");
        System.out.printf(Locale.ROOT, "数据集: %d 个普通 key + %d 个 NBT key = %d 个 key；所有数量均不超过 Long.MAX_VALUE%n%n",
                ORDINARY_ITEM_TYPES, NBT_ITEM_TYPES, STANDARD_ITEM_TYPES);
        for (LongScenario scenario : LongScenario.values()) {
            LongMeasurement measurement = compareLong(scenario);
            longMeasurements.add(measurement);
            printLongMeasurement(measurement);
        }

        System.out.println("=== 三方真实 StorageCell 并发对比 ===");
        System.out.printf(Locale.ROOT, "并发线程数: %d；并发数据集: %d 个 key；每个线程操作 %d 个 key%n%n",
                concurrentThreads, concurrentWorkload.keys.length, concurrentWorkload.keys.length);
        for (ConcurrentScenario scenario : ConcurrentScenario.values()) {
            ConcurrentMeasurement measurement = compareConcurrent(scenario);
            concurrentMeasurements.add(measurement);
            printConcurrentMeasurement(measurement);
        }

        verifyDevourerSimulatedExtraction();

        writeReport(reportPath, measurements, longMeasurements, concurrentMeasurements);
        writeLegacyComparisonReport(reportPath.resolveSibling(
                "infinity-storage-cell-comparison-pre-optimization.md"),
                measurements, longMeasurements, concurrentMeasurements);
        System.out.println("对比报告: " + reportPath.toAbsolutePath());
    }

    private void verifyDevourerSimulatedExtraction() {
        InfinityStorageManager manager = new InfinityStorageManager();
        ItemStack stack = new ItemStack(devourerItem);
        InfinityBigIntegerCellInventory cell = InfinityBigIntegerCellInventory.createInventory(
                stack, NOOP_SAVE_PROVIDER, manager);
        AEKey longKey = standardWorkload.keys[0];
        AEKey bigKey = standardWorkload.keys[1];

        assertExtracted("空盘模拟输出", 0,
                cell.extract(longKey, 1, Actionable.SIMULATE, ACTION_SOURCE));
        assertExtracted("long 数量写入", 100,
                cell.insert(longKey, 100, Actionable.MODULATE, ACTION_SOURCE));
        assertExtracted("long 数量模拟输出", 40,
                cell.extract(longKey, 40, Actionable.SIMULATE, ACTION_SOURCE));
        assertExtracted("long 数量模拟输出上限", 100,
                cell.extract(longKey, 200, Actionable.SIMULATE, ACTION_SOURCE));

        cell.insert(bigKey, Long.MAX_VALUE, Actionable.MODULATE, ACTION_SOURCE);
        cell.insert(bigKey, 1, Actionable.MODULATE, ACTION_SOURCE);
        BigInteger totalBeforeSimulation = manager.getCell(cell.getUUID()).getItemCount();
        assertExtracted("BigInteger 数量模拟输出", Long.MAX_VALUE,
                cell.extract(bigKey, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE));
        BigInteger totalAfterSimulation = manager.getCell(cell.getUUID()).getItemCount();
        if (!totalBeforeSimulation.equals(totalAfterSimulation)) {
            throw new AssertionError("模拟输出修改了吞噬盘库存总数");
        }

        UUID uuid = cell.getUUID();
        InfinityBigIntegerCellInventory secondView = InfinityBigIntegerCellInventory.createInventory(
                stack.copy(), NOOP_SAVE_PROVIDER, manager);
        assertExtracted("缓存建立", 100,
                secondView.extract(longKey, 200, Actionable.SIMULATE, ACTION_SOURCE));

        InfinityDataStorage replacement = new InfinityDataStorage();
        replacement.insert(longKey, 250);
        manager.updateCell(uuid, replacement);
        assertExtracted("缓存替换失效", 200,
                secondView.extract(longKey, 200, Actionable.SIMULATE, ACTION_SOURCE));

        manager.removeCell(uuid);
        assertExtracted("缓存删除失效", 0,
                secondView.extract(longKey, 200, Actionable.SIMULATE, ACTION_SOURCE));
    }

    private static void assertExtracted(String stage, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(stage + "结果不一致: " + expected + " != " + actual);
        }
    }

    private Measurement compare(Scenario scenario) {
        for (int i = 0; i < warmupRounds; i++) {
            runThreeSamples(scenario, i);
        }

        long[] optimizedSamples = new long[measureRounds];
        long[] legacySamples = new long[measureRounds];
        long[] omniSamples = new long[measureRounds];
        CellResult expected = null;
        for (int i = 0; i < measureRounds; i++) {
            Sample[] samples = runThreeSamples(scenario, i + warmupRounds);
            Sample optimized = samples[0];
            Sample legacy = samples[1];
            Sample omni = samples[2];

            if (!optimized.result().sameState(legacy.result())
                    || !optimized.result().sameState(omni.result())) {
                throw new AssertionError(scenario.label + " returned different states: "
                        + optimized.result() + " != " + legacy.result() + " != " + omni.result());
            }
            if (expected == null) {
                expected = optimized.result();
            } else if (!expected.sameState(optimized.result())) {
                throw new AssertionError(scenario.label + " was not stable across rounds: "
                        + expected + " != " + optimized.result());
            }
            optimizedSamples[i] = optimized.elapsedNanos();
            legacySamples[i] = legacy.elapsedNanos();
            omniSamples[i] = omni.elapsedNanos();
        }

        return new Measurement(scenario, average(optimizedSamples), average(legacySamples),
                average(omniSamples), expected);
    }

    /**
     * 比较当前吞噬盘、未优化吞噬盘和 Omni 的 long 盘。
     *
     * <p>这组用例故意使用 Omni 的 {@code AEUniversalCellInventory}，不使用 Omni
     * BigInteger 盘；三种实现接收完全相同的 key、数量和预置状态。</p>
     */
    private LongMeasurement compareLong(LongScenario scenario) {
        for (int i = 0; i < warmupRounds; i++) {
            runThreeLongSamples(scenario, i);
        }

        long[] optimizedSamples = new long[measureRounds];
        long[] legacySamples = new long[measureRounds];
        long[] omniSamples = new long[measureRounds];
        CellResult expected = null;
        for (int i = 0; i < measureRounds; i++) {
            Sample[] samples = runThreeLongSamples(scenario, i + warmupRounds);
            Sample optimized = samples[0];
            Sample legacy = samples[1];
            Sample omni = samples[2];

            if (!optimized.result().sameState(legacy.result())
                    || !optimized.result().sameState(omni.result())) {
                throw new AssertionError(scenario.label + " returned different states: "
                        + optimized.result() + " != " + legacy.result() + " != " + omni.result());
            }
            if (expected == null) {
                expected = optimized.result();
            } else if (!expected.sameState(optimized.result())) {
                throw new AssertionError(scenario.label + " was not stable across rounds: "
                        + expected + " != " + optimized.result());
            }
            optimizedSamples[i] = optimized.elapsedNanos();
            legacySamples[i] = legacy.elapsedNanos();
            omniSamples[i] = omni.elapsedNanos();
        }

        return new LongMeasurement(scenario, average(optimizedSamples), average(legacySamples),
                average(omniSamples), expected);
    }

    private Sample[] runThreeLongSamples(LongScenario scenario, int order) {
        Sample[] samples = new Sample[3];
        switch (Math.floorMod(order, 3)) {
            case 0 -> {
                samples[0] = runLongSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runLongSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runLongSample(scenario, Disk.OMNI_LONG);
            }
            case 1 -> {
                samples[2] = runLongSample(scenario, Disk.OMNI_LONG);
                samples[0] = runLongSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runLongSample(scenario, Disk.LEGACY_DEVOURER);
            }
            case 2 -> {
                samples[1] = runLongSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runLongSample(scenario, Disk.OMNI_LONG);
                samples[0] = runLongSample(scenario, Disk.OPTIMIZED_DEVOURER);
            }
            default -> throw new AssertionError("Unexpected long sample order");
        }
        return samples;
    }

    private Sample runLongSample(LongScenario scenario, Disk disk) {
        StorageCell cell = createCell(disk, longWorkload, scenario.requiresSeed, LONG_QUANTITY_AMOUNT);
        long start = System.nanoTime();
        long returned = 0;
        KeyCounter terminalContents = null;
        switch (scenario.operation) {
            case INPUT_SIMULATE -> returned = runInput(cell, longWorkload, Actionable.SIMULATE);
            case INPUT_MODULATE -> returned = runInput(cell, longWorkload, Actionable.MODULATE);
            case OUTPUT_SIMULATE -> returned = runOutput(cell, longWorkload, Actionable.SIMULATE);
            case OUTPUT_MODULATE -> returned = runOutput(cell, longWorkload, Actionable.MODULATE);
            case TERMINAL_READ -> {
                terminalContents = new KeyCounter();
                cell.getAvailableStacks(terminalContents);
            }
            default -> throw new AssertionError("Unhandled long operation: " + scenario.operation);
        }
        long elapsed = System.nanoTime() - start;
        CellResult result = terminalContents == null
                ? stateResult(returned, cell)
                : summarize(terminalContents, terminalContents.size(), cell.getStatus());
        return new Sample(elapsed, result);
    }

    private Sample[] runThreeSamples(Scenario scenario, int order) {
        Sample[] samples = new Sample[3];
        switch (Math.floorMod(order, 3)) {
            case 0 -> {
                samples[0] = runSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runSample(scenario, Disk.OMNI);
            }
            case 1 -> {
                samples[2] = runSample(scenario, Disk.OMNI);
                samples[0] = runSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runSample(scenario, Disk.LEGACY_DEVOURER);
            }
            case 2 -> {
                samples[1] = runSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runSample(scenario, Disk.OMNI);
                samples[0] = runSample(scenario, Disk.OPTIMIZED_DEVOURER);
            }
            default -> throw new AssertionError("Unexpected sample order");
        }
        return samples;
    }

    private Sample runSample(Scenario scenario, Disk disk) {
        Workload workload = workload(scenario);
        StorageCell cell = createCell(disk, workload, scenario.requiresSeed);
        long start = System.nanoTime();
        long returned = 0;
        KeyCounter terminalContents = null;
        switch (scenario.operation) {
            case INPUT_SIMULATE -> returned = runInput(cell, workload, Actionable.SIMULATE);
            case INPUT_MODULATE -> returned = runInput(cell, workload, Actionable.MODULATE);
            case OUTPUT_SIMULATE -> returned = runOutput(cell, workload, Actionable.SIMULATE);
            case OUTPUT_MODULATE -> returned = runOutput(cell, workload, Actionable.MODULATE);
            case TERMINAL_READ -> {
                terminalContents = new KeyCounter();
                cell.getAvailableStacks(terminalContents);
            }
            default -> throw new AssertionError("Unhandled operation: " + scenario.operation);
        }
        long elapsed = System.nanoTime() - start;
        CellResult result = terminalContents == null
                ? stateResult(returned, cell)
                : summarize(terminalContents, terminalContents.size(), cell.getStatus());
        return new Sample(elapsed, result);
    }

    private Workload workload(Scenario scenario) {
        return scenario.workloadKind == WorkloadKind.STANDARD ? standardWorkload : millionWorkload;
    }

    private StorageCell createCell(Disk disk, Workload workload, boolean seed) {
        return createCell(disk, workload, seed, SEED_AMOUNT);
    }

    private StorageCell createCell(Disk disk, Workload workload, boolean seed, long seedAmount) {
        if (seed && workload.keys.length == MILLION_KEY_TYPES) {
            return createMillionSeededCell(disk, workload);
        }

        StorageCell cell = createEmptyCell(disk, workload);
        if (seed) {
            seed(cell, workload, seedAmount);
        }
        return cell;
    }

    private StorageCell createEmptyCell(Disk disk, Workload workload) {
        StorageCell cell;
        switch (disk) {
            case OPTIMIZED_DEVOURER -> {
                InfinityStorageManager manager = new InfinityStorageManager();
                cell = InfinityBigIntegerCellInventory.createInventory(
                        new ItemStack(devourerItem), NOOP_SAVE_PROVIDER, manager);
            }
            case LEGACY_DEVOURER -> {
                LegacyInfinityBigIntegerCellInventory.LegacyInfinityStorageManager manager =
                        new LegacyInfinityBigIntegerCellInventory.LegacyInfinityStorageManager();
                cell = LegacyInfinityBigIntegerCellInventory.createInventory(
                        new ItemStack(devourerItem), NOOP_SAVE_PROVIDER, manager);
            }
            case OMNI -> {
                Object2ObjectOpenHashMap<AEKey, BigInteger> storage =
                        new Object2ObjectOpenHashMap<>(workload.keys.length);
                AEBigIntegerCellData data = new AEBigIntegerCellData(storage);
                cell = new AEBigIntegerCellInventory(data, new ItemStack(omniItem), omniItem, NOOP_SAVE_PROVIDER);
            }
            case OMNI_LONG -> {
                Object2LongOpenHashMap<AEKey> storage = new Object2LongOpenHashMap<>(workload.keys.length);
                storage.defaultReturnValue(0L);
                AEUniversalCellData data = new AEUniversalCellData(storage);
                cell = new AEUniversalCellInventory(
                        data, new ItemStack(omniLongItem), omniLongItem, NOOP_SAVE_PROVIDER);
            }
            default -> throw new AssertionError("Unexpected disk target: " + disk);
        }
        return cell;
    }

    /**
     * Million-key output/read cases only need an equivalent pre-populated state.
     * Building it through one million public insert calls would benchmark setup,
     * and the old implementation can spend more than the server watchdog limit
     * on that setup. This path is deliberately outside the timed section.
     */
    private StorageCell createMillionSeededCell(Disk disk, Workload workload) {
        BigInteger seedAmount = BigInteger.valueOf(SEED_AMOUNT);
        switch (disk) {
            case OPTIMIZED_DEVOURER -> {
                UUID uuid = UUID.randomUUID();
                ItemStack stack = new ItemStack(devourerItem);
                CompoundTag tag = new CompoundTag();
                tag.putUUID(com.extendedae_plus.util.storage.InfinityConstants.INFINITY_CELL_UUID, uuid);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                InfinityStorageManager manager = new InfinityStorageManager();
                Object2LongOpenHashMap<AEKey> longAmounts = new Object2LongOpenHashMap<>(workload.keys.length);
                longAmounts.defaultReturnValue(0L);
                for (AEKey key : workload.keys) {
                    longAmounts.put(key, SEED_AMOUNT);
                }
                InfinityDataStorage storage = InfinityDataStorage.fromLongAmounts(longAmounts);
                manager.updateCell(uuid, storage);
                return InfinityBigIntegerCellInventory.createInventory(
                        stack, NOOP_SAVE_PROVIDER, manager);
            }
            case LEGACY_DEVOURER -> {
                UUID uuid = UUID.randomUUID();
                ItemStack stack = new ItemStack(devourerItem);
                CompoundTag tag = new CompoundTag();
                tag.putUUID(com.extendedae_plus.util.storage.InfinityConstants.INFINITY_CELL_UUID, uuid);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                LegacyInfinityBigIntegerCellInventory.LegacyInfinityStorageManager manager =
                        new LegacyInfinityBigIntegerCellInventory.LegacyInfinityStorageManager();
                manager.updateCell(uuid,
                        LegacyInfinityBigIntegerCellInventory.createSeededStorage(
                                workload.keys, SEED_AMOUNT));
                return LegacyInfinityBigIntegerCellInventory.createInventory(
                        stack, NOOP_SAVE_PROVIDER, manager);
            }
            case OMNI -> {
                Object2ObjectOpenHashMap<AEKey, BigInteger> storage =
                        new Object2ObjectOpenHashMap<>(workload.keys.length);
                for (AEKey key : workload.keys) {
                    storage.put(key, seedAmount);
                }
                AEBigIntegerCellData data = new AEBigIntegerCellData(storage);
                return new AEBigIntegerCellInventory(
                        data, new ItemStack(omniItem), omniItem, NOOP_SAVE_PROVIDER);
            }
            case OMNI_LONG -> {
                Object2LongOpenHashMap<AEKey> storage = new Object2LongOpenHashMap<>(workload.keys.length);
                storage.defaultReturnValue(0L);
                for (AEKey key : workload.keys) {
                    storage.put(key, SEED_AMOUNT);
                }
                AEUniversalCellData data = new AEUniversalCellData(storage);
                return new AEUniversalCellInventory(
                        data, new ItemStack(omniLongItem), omniLongItem, NOOP_SAVE_PROVIDER);
            }
            default -> throw new AssertionError("Unexpected disk target: " + disk);
        }
    }

    private void seed(StorageCell cell, Workload workload) {
        seed(cell, workload, SEED_AMOUNT);
    }

    private void seed(StorageCell cell, Workload workload, long seedAmount) {
        for (int i = 0; i < workload.keys.length; i++) {
            long inserted = cell.insert(workload.keys[i], seedAmount, Actionable.MODULATE, ACTION_SOURCE);
            if (inserted != seedAmount) {
                throw new AssertionError("Seed failed for key " + i + ": inserted " + inserted);
            }
        }
    }

    private long runInput(StorageCell cell, Workload workload, Actionable action) {
        long returned = 0;
        for (int i = 0; i < workload.keys.length; i++) {
            returned += cell.insert(workload.keys[i], workload.amounts[i], action, ACTION_SOURCE);
        }
        return returned;
    }

    private long runOutput(StorageCell cell, Workload workload, Actionable action) {
        long returned = 0;
        for (int i = 0; i < workload.keys.length; i++) {
            returned += cell.extract(workload.keys[i], workload.amounts[i], action, ACTION_SOURCE);
        }
        return returned;
    }

    private ConcurrentMeasurement compareConcurrent(ConcurrentScenario scenario) {
        for (int i = 0; i < warmupRounds; i++) {
            runThreeConcurrentSamples(scenario, i);
        }

        long[] optimizedSamples = new long[measureRounds];
        long[] legacySamples = new long[measureRounds];
        long[] omniSamples = new long[measureRounds];
        CellResult expected = null;
        for (int i = 0; i < measureRounds; i++) {
            ConcurrentSample[] samples = runThreeConcurrentSamples(scenario, i + warmupRounds);
            ConcurrentSample optimized = samples[0];
            ConcurrentSample legacy = samples[1];
            ConcurrentSample omni = samples[2];

            if (!optimized.result().sameState(legacy.result())
                    || !optimized.result().sameState(omni.result())) {
                throw new AssertionError(scenario.label(concurrentThreads) + " returned different states: "
                        + optimized.result() + " != " + legacy.result() + " != " + omni.result());
            }
            if (expected == null) {
                expected = optimized.result();
            } else if (!expected.sameState(optimized.result())) {
                throw new AssertionError(scenario.label(concurrentThreads) + " was not stable across rounds: "
                        + expected + " != " + optimized.result());
            }
            optimizedSamples[i] = optimized.elapsedNanos();
            legacySamples[i] = legacy.elapsedNanos();
            omniSamples[i] = omni.elapsedNanos();
        }

        return new ConcurrentMeasurement(
                scenario, average(optimizedSamples), average(legacySamples),
                average(omniSamples), expected);
    }

    private ConcurrentSample[] runThreeConcurrentSamples(ConcurrentScenario scenario, int order) {
        ConcurrentSample[] samples = new ConcurrentSample[3];
        switch (Math.floorMod(order, 3)) {
            case 0 -> {
                samples[0] = runConcurrentSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runConcurrentSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runConcurrentSample(scenario, Disk.OMNI);
            }
            case 1 -> {
                samples[2] = runConcurrentSample(scenario, Disk.OMNI);
                samples[0] = runConcurrentSample(scenario, Disk.OPTIMIZED_DEVOURER);
                samples[1] = runConcurrentSample(scenario, Disk.LEGACY_DEVOURER);
            }
            case 2 -> {
                samples[1] = runConcurrentSample(scenario, Disk.LEGACY_DEVOURER);
                samples[2] = runConcurrentSample(scenario, Disk.OMNI);
                samples[0] = runConcurrentSample(scenario, Disk.OPTIMIZED_DEVOURER);
            }
            default -> throw new AssertionError("Unexpected concurrent sample order");
        }
        return samples;
    }

    private ConcurrentSample runConcurrentSample(ConcurrentScenario scenario, Disk disk) {
        int cellCount = scenario.group == ConcurrencyGroup.SHARED ? 1 : concurrentThreads;
        StorageCell[] cells = new StorageCell[cellCount];
        for (int i = 0; i < cellCount; i++) {
            cells[i] = createConcurrentCell(disk, scenario);
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
                        start.await();
                        StorageCell cell = cells[scenario.group == ConcurrencyGroup.SHARED ? 0 : index];
                        long returned = 0;
                        for (int keyIndex = 0; keyIndex < concurrentWorkload.keys.length; keyIndex++) {
                            if (scenario.group == ConcurrencyGroup.SHARED) {
                                synchronized (cell) {
                                    returned += applyConcurrentOperation(scenario, cell, keyIndex);
                                }
                            } else {
                                returned += applyConcurrentOperation(scenario, cell, keyIndex);
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

            awaitLatch(ready, "concurrent workers ready");
            long startNanos = System.nanoTime();
            start.countDown();
            awaitLatch(done, "concurrent workers done");
            long elapsedNanos = System.nanoTime() - startNanos;

            if (!failures.isEmpty()) {
                Throwable failure = failures.get(0);
                throw new IllegalStateException("Concurrent " + scenario.label(concurrentThreads)
                        + " failed", failure);
            }

            long returned = 0;
            for (long threadReturned : returnedByThread) {
                returned += threadReturned;
            }
            return new ConcurrentSample(elapsedNanos, concurrentStateResult(cells, returned));
        } finally {
            executor.shutdownNow();
        }
    }

    private StorageCell createConcurrentCell(Disk disk, ConcurrentScenario scenario) {
        StorageCell cell = createCell(disk, concurrentWorkload, false);
        boolean needsSeed = scenario.operation == ConcurrentOperation.OUTPUT
                || scenario.quantityMode == QuantityMode.FEW_BIGINTEGER;
        if (needsSeed) {
            seed(cell, concurrentWorkload);
        }

        if (scenario.quantityMode == QuantityMode.FEW_BIGINTEGER) {
            int bigCount = Math.max(1,
                    concurrentWorkload.keys.length * CONCURRENT_BIG_PERCENT / 100);
            for (int i = 0; i < bigCount; i++) {
                long inserted = cell.insert(
                        concurrentWorkload.keys[i], Long.MAX_VALUE,
                        Actionable.MODULATE, ACTION_SOURCE);
                if (inserted != Long.MAX_VALUE) {
                    throw new AssertionError("Unable to promote concurrent key " + i
                            + " to BigInteger: " + inserted);
                }
                inserted = cell.insert(
                        concurrentWorkload.keys[i], 1,
                        Actionable.MODULATE, ACTION_SOURCE);
                if (inserted != 1) {
                    throw new AssertionError("Unable to extend concurrent BigInteger key " + i
                            + ": " + inserted);
                }
            }
        }
        return cell;
    }

    private long applyConcurrentOperation(ConcurrentScenario scenario, StorageCell cell, int keyIndex) {
        AEKey key = concurrentWorkload.keys[keyIndex];
        long amount = concurrentWorkload.amounts[keyIndex];
        return scenario.operation == ConcurrentOperation.INPUT
                ? cell.insert(key, amount, Actionable.MODULATE, ACTION_SOURCE)
                : cell.extract(key, amount, Actionable.MODULATE, ACTION_SOURCE);
    }

    private CellResult concurrentStateResult(StorageCell[] cells, long returned) {
        KeyCounter available = new KeyCounter();
        CellState status = CellState.EMPTY;
        for (StorageCell cell : cells) {
            cell.getAvailableStacks(available);
            if (cell.getStatus() == CellState.NOT_EMPTY) {
                status = CellState.NOT_EMPTY;
            }
        }
        return summarize(available, returned, status);
    }

    private static void awaitLatch(CountDownLatch latch, String phase) {
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out waiting for " + phase);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + phase, exception);
        }
    }

    private CellResult stateResult(long returned, StorageCell cell) {
        KeyCounter available = new KeyCounter();
        cell.getAvailableStacks(available);
        return summarize(available, returned, cell.getStatus());
    }

    private CellResult summarize(KeyCounter available, long returned, CellState status) {
        long checksum = 0;
        BigInteger total = BigInteger.ZERO;
        int count = 0;
        for (var entry : available) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            checksum += key.hashCode() * CHECKSUM_MULTIPLIER + amount;
            total = total.add(BigInteger.valueOf(amount));
            count++;
        }
        return new CellResult(returned, total, count, checksum, status);
    }

    private Workload createStandardWorkload() {
        AEKey[] keys = new AEKey[STANDARD_ITEM_TYPES];
        for (int i = 0; i < ORDINARY_ITEM_TYPES; i++) {
            Item ordinaryItem = ModItems.CELL_BENCHMARK_ORDINARY_ITEMS.get(i).get();
            keys[i] = AEItemKey.of(new ItemStack(ordinaryItem));
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

    private Workload createLongWorkload() {
        long[] amounts = new long[STANDARD_ITEM_TYPES];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = LONG_QUANTITY_AMOUNT;
        }
        return new Workload(standardWorkload.keys, amounts);
    }

    private Workload createConcurrentWorkload(int count) {
        if (count <= 0 || count > standardWorkload.keys.length) {
            throw new IllegalArgumentException("Concurrent key count must be in 1.."
                    + standardWorkload.keys.length);
        }

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
        return new Workload(keys, createAmounts(MILLION_KEY_TYPES, 0x1234ABCDL));
    }

    private long[] createAmounts(int count, long initialValue) {
        long[] result = new long[count];
        long value = initialValue;
        for (int i = 0; i < result.length; i++) {
            value = value * 2_862_933_555_777_941_757L + 3_039_700_493L;
            result[i] = 1L + Long.remainderUnsigned(value >>> 17, 1_024L);
        }
        return result;
    }

    private void printMeasurement(Measurement measurement) {
        long operationCount = operationCount(measurement.scenario);
        double optimizedThroughput = throughput(operationCount, measurement.optimizedNanos);
        double legacyThroughput = throughput(operationCount, measurement.legacyNanos);
        double omniThroughput = throughput(operationCount, measurement.omniNanos);
        System.out.println("[" + measurement.scenario.label + "]");
        System.out.printf(Locale.ROOT, "  优化后吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.optimizedNanos), optimizedThroughput);
        System.out.printf(Locale.ROOT, "  未优化吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.legacyNanos), legacyThroughput);
        System.out.printf(Locale.ROOT, "  Omni:   %s，%,.0f 次/秒%n",
                formatDuration(measurement.omniNanos), omniThroughput);
        System.out.printf(Locale.ROOT, "  优化后/Omni 性能: %.2fx；未优化/Omni 性能: %.2fx；优化后/未优化性能: %.2fx；结果: %d 个 key，%s%n%n",
                ratio(measurement.omniNanos, measurement.optimizedNanos),
                ratio(measurement.omniNanos, measurement.legacyNanos),
                ratio(measurement.legacyNanos, measurement.optimizedNanos),
                measurement.result.itemCount, measurement.result.status);
    }

    private void printConcurrentMeasurement(ConcurrentMeasurement measurement) {
        long operationCount = concurrentOperationCount();
        double optimizedThroughput = throughput(operationCount, measurement.optimizedNanos);
        double legacyThroughput = throughput(operationCount, measurement.legacyNanos);
        double omniThroughput = throughput(operationCount, measurement.omniNanos);
        System.out.println("[" + measurement.scenario.label(concurrentThreads) + "]");
        System.out.printf(Locale.ROOT, "  优化后吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.optimizedNanos), optimizedThroughput);
        System.out.printf(Locale.ROOT, "  未优化吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.legacyNanos), legacyThroughput);
        System.out.printf(Locale.ROOT, "  Omni:   %s，%,.0f 次/秒%n",
                formatDuration(measurement.omniNanos), omniThroughput);
        System.out.printf(Locale.ROOT, "  优化后/Omni 性能: %.2fx；未优化/Omni 性能: %.2fx；优化后/未优化性能: %.2fx；结果: %d 个 key，%s%n%n",
                ratio(measurement.omniNanos, measurement.optimizedNanos),
                ratio(measurement.omniNanos, measurement.legacyNanos),
                ratio(measurement.legacyNanos, measurement.optimizedNanos),
                measurement.result.itemCount, measurement.result.status);
    }

    private void printLongMeasurement(LongMeasurement measurement) {
        long operationCount = longOperationCount(measurement.scenario);
        double optimizedThroughput = throughput(operationCount, measurement.optimizedNanos);
        double legacyThroughput = throughput(operationCount, measurement.legacyNanos);
        double omniThroughput = throughput(operationCount, measurement.omniNanos);
        System.out.println("[long 数量级 / " + measurement.scenario.label + "]");
        System.out.printf(Locale.ROOT, "  优化后吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.optimizedNanos), optimizedThroughput);
        System.out.printf(Locale.ROOT, "  未优化吞噬盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.legacyNanos), legacyThroughput);
        System.out.printf(Locale.ROOT, "  Omni long 盘: %s，%,.0f 次/秒%n",
                formatDuration(measurement.omniNanos), omniThroughput);
        System.out.printf(Locale.ROOT, "  优化后/Omni long: %.2fx；未优化/Omni long: %.2fx；优化后/未优化: %.2fx；结果: %d 个 key，%s%n%n",
                ratio(measurement.omniNanos, measurement.optimizedNanos),
                ratio(measurement.omniNanos, measurement.legacyNanos),
                ratio(measurement.legacyNanos, measurement.optimizedNanos),
                measurement.result.itemCount, measurement.result.status);
    }

    private void writeReport(Path reportPath, List<Measurement> measurements,
                             List<LongMeasurement> longMeasurements,
                             List<ConcurrentMeasurement> concurrentMeasurements) {
        StringBuilder report = new StringBuilder();
        report.append("# ExtendedAE Plus 三方 StorageCell 同期性能对比报告\n\n");
        report.append("## 测试环境与数据\n\n");
        report.append("- 对比对象：当前优化后的吞噬盘、未优化吞噬盘、Omni Cells 无限盘。\n");
        report.append("- 优化后吞噬盘：当前工作区的 `InfinityBigIntegerCellInventory`，使用 long/BigInteger 分离存储和库存对象缓存。\n");
        report.append("- 未优化吞噬盘：Git 提交 `c8274318` 的父提交 `94fefbc9` 中的真实吞噬盘 StorageCell 代码快照；保留旧版 HashMap、单一 BigInteger Map、UUID 和 StorageManager 路径。\n");
        report.append("- Omni BigInteger 对照组：`ae2omnicells-1.1.6-1.21.1-neoforge.jar` 的 `AEBigIntegerCellInventory`。\n");
        report.append("- Omni long 对照组：同一 JAR 的 `CREATIVE_AE_CELL_LONG`，真实实现为 `AEUniversalCellInventory`；本组不使用 Omni BigInteger 盘，也不使用任何旧适配器。\n");
        report.append("- 运行环境：Minecraft 1.21.1、NeoForge 21.1.216、Java 21；专用 server run 使用 `-Xms1G -Xmx4G -XX:+UseSerialGC`。\n");
        report.append("- 标准数据集：4,096 个普通物品 key + 4,096 个不同 `CUSTOM_DATA.benchmark_variant` 的 NBT key，共 8,192 个 key。\n");
        report.append("- 百万数据集：复用 1 个已注册 NBT 物品，生成 `benchmark_variant=0..999999` 的 1,000,000 个不同 key，不注册一百万个物品。\n");
        report.append("- 并发数据集：从标准数据集中均匀抽取 256 个 key，普通 key 和 NBT key 各 128 个；每个线程遍历全部 256 个 key。\n");
        report.append("- BigInteger 对照组数量：每个预置 key 为 ").append(SEED_AMOUNT).append("；普通输入数量为确定性生成的 1..1024；混合数量场景中 5% key 预置为超过 `Long.MAX_VALUE` 的 BigInteger。\n");
        report.append("- long 数量级对照组数量：每个 key 预置及请求 ").append(LONG_QUANTITY_AMOUNT)
                .append("；该值约为 `Long.MAX_VALUE / 8,192`，每个 key 和全体 key 的累计结果均不溢出 long。\n");
        report.append("- 测量方法：").append(warmupRounds).append(" 轮预热，").append(measureRounds)
                .append(" 轮测量，报告算术平均值；三种实现按轮次轮换执行顺序。\n");
        report.append("- 普通用例计时：输入/输出只计真实 `StorageCell.insert`/`extract` 调用；AE 终端读取只计 `getAvailableStacks(KeyCounter)`。\n");
        report.append("- 并发用例计时：只计所有 worker 同时放行后的操作阶段；建盘、预置、线程池创建、就绪等待、汇总、校验、`persist` 和报告写入不计时。\n\n");

        report.append("## 结果口径与输入模拟说明\n\n");
        report.append("- `某实现性能 / Omni性能` = Omni 耗时 / 某实现耗时；大于 1 表示该实现比 Omni 快，小于 1 表示 Omni 更快。\n");
        report.append("- `优化后 / 未优化性能` = 未优化吞噬盘耗时 / 优化后吞噬盘耗时；大于 1 表示优化后更快。\n");
        Measurement inputSimulation = measurements.stream()
                .filter(measurement -> measurement.scenario == Scenario.INPUT_SIMULATE)
                .findFirst().orElse(null);
        if (inputSimulation != null) {
            report.append("\n## 输入模拟结论\n\n");
            report.append(String.format(Locale.ROOT,
                    "本轮输入 `SIMULATE`：优化后吞噬盘 %s，未优化真实吞噬盘 %s，Omni %s。\n",
                    formatDuration(inputSimulation.optimizedNanos),
                    formatDuration(inputSimulation.legacyNanos),
                    formatDuration(inputSimulation.omniNanos)));
            report.append(String.format(Locale.ROOT,
                    "优化后吞噬盘相对未优化真实吞噬盘为 %.2fx；相对 Omni 为 %.2fx。后一个倍率小于 1 只表示本轮 Omni 在该路径更快，不表示优化后吞噬盘比旧版回退。\n",
                    ratio(inputSimulation.legacyNanos, inputSimulation.optimizedNanos),
                    ratio(inputSimulation.omniNanos, inputSimulation.optimizedNanos)));
            report.append("原因是旧版 `insert(SIMULATE)` 返回前仍会调用 `getWritableCellStorage()`：首次调用需要写入 UUID 并创建 StorageManager 条目，后续调用还要查旧版 HashMap；当前版完成必要过滤后直接返回，不分配 UUID、不创建存储条目。\n\n");
        }
        report.append("- 旧版输入模拟不是简化 Map 测试：旧代码即使是 `SIMULATE`，仍会进行分区过滤、UUID 读取或分配、StorageManager 查找或创建以及 BigInteger Map 查找；当前优化版在相同过滤检查后直接返回，不分配 UUID、不创建存储条目。\n");
        report.append("- 因此此前“简化替身约 196 us、当前约 404 us”的结论无效；约 196 us 测到的是省略了真实吞噬盘外围流程的测试替身，不是未优化真实吞噬盘。\n");
        report.append("- 8,192 次输入模拟总耗时处于亚毫秒级；2 轮预热和 10 轮平均仍会受到 JIT、线程调度和短暂停顿影响。若需要严格回归，应提高单样本工作量并记录原始样本、 中位数和分位数。\n\n");

        report.append("## Omni JAR 对可比性的影响\n\n");
        report.append("- 三种实现都只通过 AE2 共同的 `StorageCell` 接口调用；Omni 的调用不会转发到任一吞噬盘实现。\n");
        report.append("- 三方都使用新建的内存库存、相同的 key、相同的预置数量和相同的操作顺序；JAR 引用不会造成调用错实现。\n");
        report.append("- 三方均未计入真实物品栏网络、客户端 GUI、磁盘存档序列化和建盘成本；AE 终端用例只测 `getAvailableStacks`。\n");
        report.append("- 当前优化点包括 long/BigInteger 分离存储、库存对象缓存和模拟提取只读路径；未优化快照保留旧版单一 BigInteger Map 路径。\n");
        report.append("- 单盘共享并发场景在每次 StorageCell 操作外加 `synchronized`，多盘独立场景每个线程使用独立盘。\n\n");

        report.append("## 测试用例说明\n\n");
        report.append("### 一、标准数据集：三方真实 StorageCell\n\n");
        report.append("| 用例 | 数据集 | 预置库存 | 操作 | 是否修改库存 | 测试目的 |\n");
        report.append("|---|---:|---|---|---|---|\n");
        report.append("|输入 SIMULATE|8,192 key|空盘|逐 key `insert`|否|对比三方真实实现的模拟输入路径；包含旧版外围流程差异|\n");
        report.append("|输入 MODULATE|8,192 key|空盘|逐 key `insert`|是|测量真实输入和 key 建立|\n");
        report.append("|输出 SIMULATE|8,192 key|每 key 1,000,000|逐 key `extract`|否|测量模拟读取，不扣减库存|\n");
        report.append("|输出 MODULATE|8,192 key|每 key 1,000,000|逐 key `extract`|是|测量真实扣减和库存更新|\n");
        report.append("|AE 终端读取|8,192 key|每 key 1,000,000|`getAvailableStacks`|否|模拟终端打开/刷新时的库存列表读取|\n\n");

        report.append("### 二、百万 key 数据集：三方真实 StorageCell\n\n");
        report.append("| 用例 | 数据集 | 预置库存 | 操作 | 默认状态 | 测试目的 |\n");
        report.append("|---|---:|---|---|---|---|\n");
        report.append("|百万 key AE 终端读取|1,000,000 个不同 NBT key|每 key 1,000,000|`getAvailableStacks`|默认执行|观察百万 key 下的终端列表读取|\n");
        report.append("|百万 key 输入 SIMULATE|1,000,000 个不同 NBT key|空盘|逐 key `insert`|完整模式执行|")
                .append("对比三方真实实现的百万 key 模拟输入|\n");
        report.append("|百万 key 输入 MODULATE|1,000,000 个不同 NBT key|空盘|逐 key `insert`|完整模式执行|观察百万 key 下的真实输入|\n");
        report.append("|百万 key 输出 SIMULATE|1,000,000 个不同 NBT key|每 key 1,000,000|逐 key `extract`|完整模式执行|观察百万 key 下的模拟输出|\n");
        report.append("|百万 key 输出 MODULATE|1,000,000 个不同 NBT key|每 key 1,000,000|逐 key `extract`|完整模式执行|观察百万 key 下的真实输出|\n\n");

        report.append("### 三、long 数量级：三方真实 StorageCell\n\n");
        report.append("本组使用与标准组相同的 8,192 个普通/NBT key，但每个 key 使用接近 long 上限的数量；Omni 对象明确为 `CREATIVE_AE_CELL_LONG` 的 `AEUniversalCellInventory`。\n\n");
        report.append("| 用例 | 数据集 | 预置库存 | 单次请求 | 操作 | 是否修改库存 | 测试目的 |\n");
        report.append("|---|---:|---|---:|---|---|---|\n");
        report.append("|输入 SIMULATE（long 数量）|8,192 key|空盘|每 key ").append(LONG_QUANTITY_AMOUNT).append("|逐 key `insert`|否|比较三方 long 数量级模拟输入路径|\n");
        report.append("|输入 MODULATE（long 数量）|8,192 key|空盘|每 key ").append(LONG_QUANTITY_AMOUNT).append("|逐 key `insert`|是|比较三方 long 数量级真实输入路径|\n");
        report.append("|输出 SIMULATE（long 数量）|8,192 key|每 key ").append(LONG_QUANTITY_AMOUNT).append("|每 key ").append(LONG_QUANTITY_AMOUNT).append("|逐 key `extract`|否|比较三方 long 数量级模拟输出路径|\n");
        report.append("|输出 MODULATE（long 数量）|8,192 key|每 key ").append(LONG_QUANTITY_AMOUNT).append("|每 key ").append(LONG_QUANTITY_AMOUNT).append("|逐 key `extract`|是|比较三方 long 数量级真实输出路径|\n");
        report.append("|AE 终端读取（long 数量）|8,192 key|每 key ").append(LONG_QUANTITY_AMOUNT).append("|—|`getAvailableStacks`|否|比较三方 long 数量级终端读取路径|\n\n");

        report.append("### 四、单盘共享并发：三方真实 StorageCell 对比\n\n");
        report.append("所有线程共享同一个真实 StorageCell")
                .append("；每次 `insert`/`extract` 外加锁。默认 ").append(concurrentThreads)
                .append(" 个线程，每个线程操作 ").append(concurrentWorkload.keys.length).append(" 个 key。\n\n");
        report.append("| 用例 | 线程数 | 数量类型 | 预置库存 | 操作 | 测试目的 |\n");
        report.append("|---|---:|---|---|---|---|\n");
        report.append("|一个盘接受并发线程输入| ").append(concurrentThreads).append(" |仅 long|空盘|输入|测量共享单盘的 long 输入路径和锁竞争|\n");
        report.append("|一个盘处理并发线程输出| ").append(concurrentThreads).append(" |仅 long|每 key 预置 long 数量|输出|测量共享单盘的 long 输出路径和锁竞争|\n");
        report.append("|单盘预置少量 BigInteger 后继续输入| ").append(concurrentThreads).append(" |long + 5% BigInteger|每 key 预置数量，并提升 5% key|输入|测量混合数量输入和 BigInteger 路径|\n");
        report.append("|单盘预置少量 BigInteger 后继续输出| ").append(concurrentThreads).append(" |long + 5% BigInteger|每 key 预置数量，并提升 5% key|输出|测量混合数量输出和 BigInteger 路径|\n\n");

        report.append("### 五、多盘独立并发：三方真实 StorageCell 对比\n\n");
        report.append("每个线程拥有一个独立真实 StorageCell")
                .append("；不共享库存 Map，用于观察多个独立盘同时输入/输出。\n\n");
        report.append("| 用例 | 线程数/盘数 | 数量类型 | 预置库存 | 操作 | 测试目的 |\n");
        report.append("|---|---:|---|---|---|---|\n");
        report.append("|多个独立盘进行高并发输入| ").append(concurrentThreads).append(" |仅 long|每盘空盘|输入|测量无共享库存竞争时的输入吞吐|\n");
        report.append("|多个独立盘进行高并发输出| ").append(concurrentThreads).append(" |仅 long|每盘预置 long 数量|输出|测量无共享库存竞争时的输出吞吐|\n");
        report.append("|多个独立盘进行混合数量高并发输入| ").append(concurrentThreads).append(" |long + 5% BigInteger|每盘预置并提升 5% key|输入|测量独立盘混合数量输入|\n");
        report.append("|多个独立盘进行混合数量高并发输出| ").append(concurrentThreads).append(" |long + 5% BigInteger|每盘预置并提升 5% key|输出|测量独立盘混合数量输出|\n\n");

        report.append("## 分组测量结果\n\n");
        report.append("`某实现性能 / Omni性能` = Omni 耗时 / 某实现耗时；大于 1 表示该实现更快，小于 1 表示 Omni 更快。\n");
        report.append("`优化后 / 未优化性能` = 未优化吞噬盘耗时 / 优化后吞噬盘耗时；大于 1 表示优化后更快。每张表只包含同一类测试。\n\n");
        appendCellMeasurementTable(report, measurements, WorkloadKind.STANDARD, "一、标准数据集结果");
        appendCellMeasurementTable(report, measurements, WorkloadKind.MILLION, "二、百万 key 结果");
        appendLongMeasurementTable(report, longMeasurements, "三、long 数量级结果（Omni long 盘）");
        appendConcurrentMeasurementTable(report, concurrentMeasurements, ConcurrencyGroup.SHARED,
                "四、单盘共享并发三方结果");
        appendConcurrentMeasurementTable(report, concurrentMeasurements, ConcurrencyGroup.INDEPENDENT,
                "五、多盘独立并发三方结果");

        report.append("## 单线程与多线程说明\n\n");
        report.append("标准和百万 key 的逐 key 场景在 server benchmark 主流程中按单线程顺序执行，用于测量基础 StorageCell 路径；新增的 8 个")
                .append("三方真实 StorageCell").append("场景使用 ")
                .append(concurrentThreads).append(" 个 worker 线程。\n");
        report.append("long 数量级章节是独立的单线程五项对比，Omni 对象固定为 `CREATIVE_AE_CELL_LONG` 的 `AEUniversalCellInventory`，不与 BigInteger Omni 表合并解释。\n");
        report.append("原有 `InfinityStoragePerformanceBenchmark` 仍然保留，具体的 FastUtil 优化前/后 8 个并发用例和实测结果见 `build/reports/infinity-storage-concurrency.md`；该报告与本报告的三方 StorageCell 比较不混用。\n\n");

        report.append("## 校验与运行说明\n\n");
        report.append("每个三方真实 StorageCell 场景都会比较三种实现的返回数量、可见总数量、key 数量、校验和与 `CellState`；并发场景还会检查所有 worker 是否正常结束，同一场景各轮结果必须稳定，否则测试失败。\n");
        report.append("AE 终端用例测量的是终端刷新时调用的 `getAvailableStacks` 接口，不是启动客户端 GUI。\n");
        report.append("默认只运行百万 key 的终端读取；百万 key 四种输入/输出可用 `-PcellBenchmarkMillionFull=true` 开启。\n");
        report.append("标准测试命令：`./gradlew runCellStorageComparisonBenchmark --no-daemon --no-configuration-cache --offline`\n");
        report.append("指定并发线程数：`-PcellBenchmarkConcurrentThreads=128`；调整预热/测量轮数：`-PcellBenchmarkWarmup=2 -PcellBenchmarkMeasure=10`。\n");
        report.append("百万完整测试命令：`./gradlew runCellStorageComparisonBenchmark -PcellBenchmarkMillionFull=true --no-daemon --no-configuration-cache --offline`\n\n");

        report.append("## 总合并对比表\n\n");
        report.append("下表最后统一合并标准数据集、百万 key、long 数量级、单盘共享并发和多盘独立并发的三方结果；long 数量级行的 Omni 列指 Omni long 盘。\n\n");
        appendCombinedMeasurementTable(report, measurements, longMeasurements, concurrentMeasurements);

        try {
            Path absoluteReport = reportPath.toAbsolutePath();
            Path parent = absoluteReport.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absoluteReport, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write comparison report: " + reportPath, exception);
        }
    }

    private void writeLegacyComparisonReport(Path reportPath, List<Measurement> measurements,
                                             List<LongMeasurement> longMeasurements,
                                             List<ConcurrentMeasurement> concurrentMeasurements) {
        StringBuilder report = new StringBuilder();
        report.append("# ExtendedAE Plus 未优化真实吞噬盘与 Omni 无限盘同期对比报告\n\n");
        report.append("本文件从同一次三方基准运行结果中提取“未优化真实吞噬盘”和 Omni 两列；\n");
        report.append("完整的优化后吞噬盘、未优化真实吞噬盘、Omni 三方结果和总合并表见\n");
        report.append("`infinity-storage-cell-comparison.md`。本文件不使用简化测试替身。\n\n");
        report.append("## 对比对象与口径\n\n");
        report.append("- 未优化真实吞噬盘：Git 提交 `c8274318` 的父提交 `94fefbc9` 中的 StorageCell 代码快照，保留旧版 HashMap、单一 BigInteger Map、UUID 和 StorageManager 路径。\n");
        report.append("- Omni BigInteger 对照组：`ae2omnicells-1.1.6-1.21.1-neoforge.jar` 的真实 `AEBigIntegerCellInventory`。\n");
        report.append("- long 数量级对照组：同一 JAR 的 `CREATIVE_AE_CELL_LONG` / `AEUniversalCellInventory`，不使用 Omni BigInteger 盘，也不使用旧适配器。\n");
        report.append("- 标准数据集：4,096 个普通物品 key + 4,096 个不同 NBT key，共 8,192 个 key。\n");
        report.append("- 测量方法：").append(warmupRounds).append(" 轮预热，").append(measureRounds)
                .append(" 轮测量，报告算术平均值；两种实现按轮次交替执行。\n");
        report.append("- `未优化吞噬盘 / Omni性能` = Omni 耗时 / 未优化吞噬盘耗时；大于 1 表示未优化吞噬盘更快。\n\n");

        report.append("## 单线程标准与百万 key 用例\n\n");
        appendLegacyCellMeasurementTable(report, measurements, WorkloadKind.STANDARD,
                "一、标准数据集结果");
        appendLegacyCellMeasurementTable(report, measurements, WorkloadKind.MILLION,
                "二、百万 key 结果");

        report.append("## long 数量级（未优化吞噬盘与 Omni long 盘）\n\n");
        report.append("本节只比较未优化吞噬盘与 Omni 的 `CREATIVE_AE_CELL_LONG`；不使用 Omni BigInteger 盘，也不称为旧适配器。\n\n");
        appendLegacyLongMeasurementTable(report, longMeasurements);

        report.append("## 128 线程并发用例\n\n");
        report.append("单盘共享场景的所有线程使用同一个 StorageCell；多盘独立场景每个线程使用独立 StorageCell。\n\n");
        appendLegacyConcurrentMeasurementTable(report, concurrentMeasurements, ConcurrencyGroup.SHARED,
                "三、单盘共享并发结果");
        appendLegacyConcurrentMeasurementTable(report, concurrentMeasurements, ConcurrencyGroup.INDEPENDENT,
                "四、多盘独立并发结果");

        report.append("## 总合并对比表\n\n");
        appendLegacyCombinedMeasurementTable(report, measurements, longMeasurements, concurrentMeasurements);
        report.append("\n数据来源：本文件与三方主报告由同一次运行生成，避免分别运行导致机器状态、JIT 和样本不一致。\n");
        writeReportFile(reportPath, report);
    }

    private void appendLegacyCellMeasurementTable(StringBuilder report, List<Measurement> measurements,
                                                  WorkloadKind kind, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 数据集 | 测试场景 | 操作次数 | 未优化真实吞噬盘平均值 | Omni平均值 | 未优化吞噬盘/Omni性能 | 未优化次/秒 | Omni次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (Measurement measurement : measurementsFor(measurements, kind)) {
            long operationCount = operationCount(measurement.scenario);
            report.append('|')
                    .append(kind == WorkloadKind.STANDARD ? "8,192 key" : "1,000,000 key").append('|')
                    .append(measurement.scenario.label).append('|').append(operationCount).append('|')
                    .append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.omniNanos))).append('|')
                    .append(measurement.result.itemCount).append('|').append(measurement.result.status).append('|')
                    .append('\n');
        }
        report.append('\n');
    }

    private void appendLegacyConcurrentMeasurementTable(StringBuilder report,
                                                         List<ConcurrentMeasurement> measurements,
                                                         ConcurrencyGroup group, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 数据集 | 测试场景 | 线程数 | 每线程 key 数 | 总操作数 | 未优化真实吞噬盘平均值 | Omni平均值 | 未优化吞噬盘/Omni性能 | 未优化次/秒 | Omni次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (ConcurrentMeasurement measurement : measurements) {
            if (measurement.scenario.group != group) {
                continue;
            }
            long operationCount = concurrentOperationCount();
            report.append("|标准并发子集（256 key）|")
                    .append(measurement.scenario.label(concurrentThreads)).append('|')
                    .append(concurrentThreads).append('|').append(concurrentWorkload.keys.length).append('|')
                    .append(operationCount).append('|').append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.omniNanos))).append('|')
                    .append(measurement.result.itemCount).append('|').append(measurement.result.status).append('|')
                    .append('\n');
        }
        report.append('\n');
    }

    private void appendLegacyCombinedMeasurementTable(StringBuilder report, List<Measurement> measurements,
                                                      List<LongMeasurement> longMeasurements,
                                                      List<ConcurrentMeasurement> concurrentMeasurements) {
        report.append("| 测试分组 | 数据集 | 测试场景 | 线程数 | 总操作数 | 未优化真实吞噬盘平均值 | Omni 对照盘平均值 | 未优化吞噬盘/Omni 对照盘性能 | 未优化次/秒 | Omni 对照盘次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (Measurement measurement : measurements) {
            long operationCount = operationCount(measurement.scenario);
            appendLegacyCombinedRow(report,
                    measurement.scenario.workloadKind == WorkloadKind.STANDARD ? "标准数据集" : "百万 key",
                    measurement.scenario.workloadKind == WorkloadKind.STANDARD ? "8,192 key" : "1,000,000 key",
                    measurement.scenario.label, 1, operationCount, measurement.legacyNanos,
                    measurement.omniNanos, measurement.result);
        }
        for (LongMeasurement measurement : longMeasurements) {
            long operationCount = longOperationCount(measurement.scenario);
            appendLegacyCombinedRow(report, "long 数量级（Omni long）", "8,192 key",
                    measurement.scenario.label, 1, operationCount,
                    measurement.legacyNanos, measurement.omniNanos, measurement.result);
        }
        for (ConcurrentMeasurement measurement : concurrentMeasurements) {
            appendLegacyCombinedRow(report, measurement.scenario.group.label,
                    "标准并发子集（256 key）", measurement.scenario.label(concurrentThreads),
                    concurrentThreads, concurrentOperationCount(), measurement.legacyNanos,
                    measurement.omniNanos, measurement.result);
        }
        report.append('\n');
    }

    private void appendLegacyCombinedRow(StringBuilder report, String group, String dataSet,
                                         String scenario, int threads, long operationCount,
                                         long legacyNanos, long omniNanos, CellResult result) {
        report.append('|').append(group).append('|').append(dataSet).append('|').append(scenario).append('|')
                .append(threads).append('|').append(operationCount).append('|')
                .append(formatDuration(legacyNanos)).append('|').append(formatDuration(omniNanos)).append('|')
                .append(String.format(Locale.ROOT, "%.2fx", ratio(omniNanos, legacyNanos))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, legacyNanos))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, omniNanos))).append('|')
                .append(result.itemCount).append('|').append(result.status).append('|').append('\n');
    }

    private static void writeReportFile(Path reportPath, StringBuilder report) {
        try {
            Path absoluteReport = reportPath.toAbsolutePath();
            Path parent = absoluteReport.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absoluteReport, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write comparison report: " + reportPath, exception);
        }
    }

    private static long average(long[] samples) {
        double total = 0;
        for (long sample : samples) {
            total += sample;
        }
        return Math.round(total / samples.length);
    }

    private static double throughput(long operationCount, long nanos) {
        return operationCount * 1_000_000_000D / nanos;
    }

    private static double ratio(long referenceNanos, long candidateNanos) {
        return (double) referenceNanos / candidateNanos;
    }

    private long operationCount(Scenario scenario) {
        return workload(scenario).keys.length;
    }

    private long concurrentOperationCount() {
        return (long) concurrentThreads * concurrentWorkload.keys.length;
    }

    private long longOperationCount(LongScenario scenario) {
        return longWorkload.keys.length;
    }

    private void appendCellMeasurementTable(StringBuilder report, List<Measurement> measurements,
                                            WorkloadKind kind, String title) {
        if (!title.isBlank()) {
            report.append("### ").append(title).append("\n\n");
        }
        report.append("| 数据集 | 测试场景 | 操作次数 | 优化后吞噬盘平均值 | 未优化吞噬盘平均值 | Omni 平均值 | 优化后/Omni性能 | 未优化/Omni性能 | 优化后/未优化性能 | 优化后次/秒 | 未优化次/秒 | Omni次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (Measurement measurement : measurementsFor(measurements, kind)) {
            long operationCount = operationCount(measurement.scenario);
            double optimizedThroughput = throughput(operationCount, measurement.optimizedNanos);
            double legacyThroughput = throughput(operationCount, measurement.legacyNanos);
            double omniThroughput = throughput(operationCount, measurement.omniNanos);
            String dataSet = measurement.scenario.workloadKind == WorkloadKind.STANDARD
                    ? "8,192 key" : "1,000,000 key";
            report.append('|').append(dataSet).append('|').append(measurement.scenario.label).append('|')
                    .append(operationCount).append('|')
                    .append(formatDuration(measurement.optimizedNanos)).append('|')
                    .append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.legacyNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", optimizedThroughput)).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", legacyThroughput)).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", omniThroughput)).append('|')
                    .append(measurement.result.itemCount).append('|')
                    .append(measurement.result.status).append('|').append('\n');
        }
        report.append('\n');
    }

    private void appendConcurrentMeasurementTable(StringBuilder report,
                                                  List<ConcurrentMeasurement> measurements,
                                                  ConcurrencyGroup group, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 数据集 | 测试场景 | 线程数 | 每线程 key 数 | 总操作数 | 优化后吞噬盘平均值 | 未优化吞噬盘平均值 | Omni 平均值 | 优化后/Omni性能 | 未优化/Omni性能 | 优化后/未优化性能 | 优化后次/秒 | 未优化次/秒 | Omni次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (ConcurrentMeasurement measurement : measurements) {
            if (measurement.scenario.group != group) {
                continue;
            }
            long operationCount = concurrentOperationCount();
            double optimizedThroughput = throughput(operationCount, measurement.optimizedNanos);
            double legacyThroughput = throughput(operationCount, measurement.legacyNanos);
            double omniThroughput = throughput(operationCount, measurement.omniNanos);
            report.append("|标准并发子集（256 key）|")
                    .append(measurement.scenario.label(concurrentThreads)).append('|')
                    .append(concurrentThreads).append('|')
                    .append(concurrentWorkload.keys.length).append('|')
                    .append(operationCount).append('|')
                    .append(formatDuration(measurement.optimizedNanos)).append('|')
                    .append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.legacyNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", optimizedThroughput)).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", legacyThroughput)).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", omniThroughput)).append('|')
                    .append(measurement.result.itemCount).append('|')
                    .append(measurement.result.status).append('|').append('\n');
        }
        report.append('\n');
    }

    private void appendLongMeasurementTable(StringBuilder report,
                                             List<LongMeasurement> measurements, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("Omni 对象为 `CREATIVE_AE_CELL_LONG` 对应的 `AEUniversalCellInventory`；本表所有数量均为 long 范围。\n\n");
        report.append("| 数据集 | 测试场景 | 操作次数 | 优化后吞噬盘平均值 | 未优化吞噬盘平均值 | Omni long 平均值 | 优化后/Omni long | 未优化/Omni long | 优化后/未优化 | 优化后次/秒 | 未优化次/秒 | Omni long 次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (LongMeasurement measurement : measurements) {
            long operationCount = longOperationCount(measurement.scenario);
            report.append("|8,192 key|").append(measurement.scenario.label).append('|')
                    .append(operationCount).append('|')
                    .append(formatDuration(measurement.optimizedNanos)).append('|')
                    .append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos, measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.legacyNanos, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, measurement.optimizedNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, measurement.omniNanos))).append('|')
                    .append(measurement.result.itemCount).append('|').append(measurement.result.status).append('|')
                    .append('\n');
        }
        report.append('\n');
    }

    private void appendLegacyLongMeasurementTable(StringBuilder report,
                                                   List<LongMeasurement> measurements) {
        report.append("| 测试场景 | 操作次数 | 未优化吞噬盘平均值 | Omni long 平均值 | 未优化/Omni long 性能 | 未优化次/秒 | Omni long 次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (LongMeasurement measurement : measurements) {
            long operationCount = longOperationCount(measurement.scenario);
            report.append('|').append(measurement.scenario.label).append('|').append(operationCount).append('|')
                    .append(formatDuration(measurement.legacyNanos)).append('|')
                    .append(formatDuration(measurement.omniNanos)).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", ratio(measurement.omniNanos,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.legacyNanos))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount,
                            measurement.omniNanos))).append('|')
                    .append(measurement.result.itemCount).append('|').append(measurement.result.status).append('|')
                    .append('\n');
        }
        report.append('\n');
    }

    private void appendCombinedMeasurementTable(StringBuilder report,
                                                List<Measurement> measurements,
                                                List<LongMeasurement> longMeasurements,
                                                List<ConcurrentMeasurement> concurrentMeasurements) {
        report.append("| 测试分组 | 数据集 | 测试场景 | 线程数 | 总操作数 | 优化后吞噬盘平均值 | 未优化吞噬盘平均值 | Omni 对照盘平均值 | 优化后/Omni 对照盘性能 | 未优化/Omni 对照盘性能 | 优化后/未优化性能 | 优化后次/秒 | 未优化次/秒 | Omni 对照盘次/秒 | 结果 key 数 | 状态 |\n");
        report.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (Measurement measurement : measurements) {
            long operationCount = operationCount(measurement.scenario);
            appendCombinedRow(report,
                    measurement.scenario.workloadKind == WorkloadKind.STANDARD ? "标准数据集" : "百万 key",
                    measurement.scenario.workloadKind == WorkloadKind.STANDARD ? "8,192 key" : "1,000,000 key",
                    measurement.scenario.label, 1, operationCount,
                    measurement.optimizedNanos, measurement.legacyNanos,
                    measurement.omniNanos, measurement.result);
        }
        for (LongMeasurement measurement : longMeasurements) {
            long operationCount = longOperationCount(measurement.scenario);
            appendCombinedRow(report, "long 数量级（Omni long）", "8,192 key",
                    measurement.scenario.label, 1, operationCount,
                    measurement.optimizedNanos, measurement.legacyNanos,
                    measurement.omniNanos, measurement.result);
        }
        for (ConcurrentMeasurement measurement : concurrentMeasurements) {
            appendCombinedRow(report, measurement.scenario.group.label,
                    "标准并发子集（256 key）", measurement.scenario.label(concurrentThreads),
                    concurrentThreads, concurrentOperationCount(),
                    measurement.optimizedNanos, measurement.legacyNanos,
                    measurement.omniNanos, measurement.result);
        }
        report.append('\n');
    }

    private void appendCombinedRow(StringBuilder report, String group, String dataSet, String scenario,
                                   int threads, long operationCount, long optimizedNanos,
                                   long legacyNanos, long omniNanos, CellResult result) {
        report.append('|').append(group).append('|').append(dataSet).append('|').append(scenario).append('|')
                .append(threads).append('|').append(operationCount).append('|')
                .append(formatDuration(optimizedNanos)).append('|').append(formatDuration(legacyNanos)).append('|')
                .append(formatDuration(omniNanos)).append('|')
                .append(String.format(Locale.ROOT, "%.2fx", ratio(omniNanos, optimizedNanos))).append('|')
                .append(String.format(Locale.ROOT, "%.2fx", ratio(omniNanos, legacyNanos))).append('|')
                .append(String.format(Locale.ROOT, "%.2fx", ratio(legacyNanos, optimizedNanos))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, optimizedNanos))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, legacyNanos))).append('|')
                .append(String.format(Locale.ROOT, "%,.0f", throughput(operationCount, omniNanos))).append('|')
                .append(result.itemCount).append('|').append(result.status).append('|').append('\n');
    }

    private List<Measurement> measurementsFor(List<Measurement> measurements, WorkloadKind kind) {
        if (kind == null) {
            return measurements;
        }
        return measurements.stream()
                .filter(measurement -> measurement.scenario.workloadKind == kind)
                .toList();
    }

    private static String formatDuration(long nanos) {
        if (nanos < 1_000) {
            return String.format(Locale.ROOT, "%,d ns", nanos);
        }
        if (nanos < 1_000_000) {
            return String.format(Locale.ROOT, "%.2f us", nanos / 1_000D);
        }
        if (nanos < 1_000_000_000) {
            return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000D);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000D);
    }

    private record Sample(long elapsedNanos, CellResult result) {
    }

    private record Measurement(Scenario scenario, long optimizedNanos, long legacyNanos,
                               long omniNanos, CellResult result) {
    }

    private record LongMeasurement(LongScenario scenario, long optimizedNanos, long legacyNanos,
                                   long omniNanos, CellResult result) {
    }

    private record ConcurrentSample(long elapsedNanos, CellResult result) {
    }

    private record ConcurrentMeasurement(ConcurrentScenario scenario,
                                         long optimizedNanos,
                                         long legacyNanos,
                                         long omniNanos,
                                         CellResult result) {
    }

    private record Workload(AEKey[] keys, long[] amounts) {
    }

    private record CellResult(long returned, BigInteger total, int itemCount, long checksum, CellState status) {
        private boolean sameState(CellResult other) {
            return returned == other.returned
                    && total.equals(other.total)
                    && itemCount == other.itemCount
                    && checksum == other.checksum
                    && status == other.status;
        }
    }

    private enum Disk {
        OPTIMIZED_DEVOURER,
        LEGACY_DEVOURER,
        OMNI,
        OMNI_LONG
    }

    private enum WorkloadKind {
        STANDARD,
        MILLION
    }

    private enum Operation {
        INPUT_SIMULATE,
        INPUT_MODULATE,
        OUTPUT_SIMULATE,
        OUTPUT_MODULATE,
        TERMINAL_READ
    }

    private enum LongScenario {
        INPUT_SIMULATE("输入 SIMULATE（long 数量）", Operation.INPUT_SIMULATE, false),
        INPUT_MODULATE("输入 MODULATE（long 数量）", Operation.INPUT_MODULATE, false),
        OUTPUT_SIMULATE("输出 SIMULATE（long 数量）", Operation.OUTPUT_SIMULATE, true),
        OUTPUT_MODULATE("输出 MODULATE（long 数量）", Operation.OUTPUT_MODULATE, true),
        TERMINAL_READ("AE 终端读取（long 数量）", Operation.TERMINAL_READ, true);

        private final String label;
        private final Operation operation;
        private final boolean requiresSeed;

        LongScenario(String label, Operation operation, boolean requiresSeed) {
            this.label = label;
            this.operation = operation;
            this.requiresSeed = requiresSeed;
        }
    }

    private enum ConcurrentOperation {
        INPUT,
        OUTPUT
    }

    private enum QuantityMode {
        LONG_ONLY,
        FEW_BIGINTEGER
    }

    private enum ConcurrencyGroup {
        SHARED("单盘共享"),
        INDEPENDENT("多盘独立");

        private final String label;

        ConcurrencyGroup(String label) {
            this.label = label;
        }
    }

    private enum ConcurrentScenario {
        SHARED_INPUT_LONG(
                "单盘共享 %d 线程输入（仅 long）", ConcurrencyGroup.SHARED,
                ConcurrentOperation.INPUT, QuantityMode.LONG_ONLY),
        SHARED_OUTPUT_LONG(
                "单盘共享 %d 线程输出（仅 long）", ConcurrencyGroup.SHARED,
                ConcurrentOperation.OUTPUT, QuantityMode.LONG_ONLY),
        SHARED_INPUT_BIG(
                "单盘共享 %d 线程输入（少量 BigInteger）", ConcurrencyGroup.SHARED,
                ConcurrentOperation.INPUT, QuantityMode.FEW_BIGINTEGER),
        SHARED_OUTPUT_BIG(
                "单盘共享 %d 线程输出（少量 BigInteger）", ConcurrencyGroup.SHARED,
                ConcurrentOperation.OUTPUT, QuantityMode.FEW_BIGINTEGER),
        INDEPENDENT_INPUT_LONG(
                "多盘独立 %d 线程输入（仅 long）", ConcurrencyGroup.INDEPENDENT,
                ConcurrentOperation.INPUT, QuantityMode.LONG_ONLY),
        INDEPENDENT_OUTPUT_LONG(
                "多盘独立 %d 线程输出（仅 long）", ConcurrencyGroup.INDEPENDENT,
                ConcurrentOperation.OUTPUT, QuantityMode.LONG_ONLY),
        INDEPENDENT_INPUT_BIG(
                "多盘独立 %d 线程输入（少量 BigInteger）", ConcurrencyGroup.INDEPENDENT,
                ConcurrentOperation.INPUT, QuantityMode.FEW_BIGINTEGER),
        INDEPENDENT_OUTPUT_BIG(
                "多盘独立 %d 线程输出（少量 BigInteger）", ConcurrencyGroup.INDEPENDENT,
                ConcurrentOperation.OUTPUT, QuantityMode.FEW_BIGINTEGER);

        private final String labelTemplate;
        private final ConcurrencyGroup group;
        private final ConcurrentOperation operation;
        private final QuantityMode quantityMode;

        ConcurrentScenario(String labelTemplate, ConcurrencyGroup group,
                           ConcurrentOperation operation, QuantityMode quantityMode) {
            this.labelTemplate = labelTemplate;
            this.group = group;
            this.operation = operation;
            this.quantityMode = quantityMode;
        }

        private String label(int threads) {
            return labelTemplate.formatted(threads);
        }
    }

    private enum Scenario {
        INPUT_SIMULATE("输入 SIMULATE", WorkloadKind.STANDARD, Operation.INPUT_SIMULATE, false, false),
        INPUT_MODULATE("输入 MODULATE", WorkloadKind.STANDARD, Operation.INPUT_MODULATE, false, false),
        OUTPUT_SIMULATE("输出 SIMULATE", WorkloadKind.STANDARD, Operation.OUTPUT_SIMULATE, true, false),
        OUTPUT_MODULATE("输出 MODULATE", WorkloadKind.STANDARD, Operation.OUTPUT_MODULATE, true, false),
        TERMINAL_READ("AE 终端读取", WorkloadKind.STANDARD, Operation.TERMINAL_READ, true, false),
        MILLION_TERMINAL_READ("百万 key AE 终端读取", WorkloadKind.MILLION, Operation.TERMINAL_READ, true, false),
        MILLION_INPUT_SIMULATE("百万 key 输入 SIMULATE", WorkloadKind.MILLION, Operation.INPUT_SIMULATE, false, true),
        MILLION_INPUT_MODULATE("百万 key 输入 MODULATE", WorkloadKind.MILLION, Operation.INPUT_MODULATE, false, true),
        MILLION_OUTPUT_SIMULATE("百万 key 输出 SIMULATE", WorkloadKind.MILLION, Operation.OUTPUT_SIMULATE, true, true),
        MILLION_OUTPUT_MODULATE("百万 key 输出 MODULATE", WorkloadKind.MILLION, Operation.OUTPUT_MODULATE, true, true);

        private final String label;
        private final WorkloadKind workloadKind;
        private final Operation operation;
        private final boolean requiresSeed;
        private final boolean millionOnly;

        Scenario(String label, WorkloadKind workloadKind, Operation operation,
                 boolean requiresSeed, boolean millionOnly) {
            this.label = label;
            this.workloadKind = workloadKind;
            this.operation = operation;
            this.requiresSeed = requiresSeed;
            this.millionOnly = millionOnly;
        }
    }

    private record Options(int warmupRounds, int measureRounds, int concurrentThreads,
                           boolean millionFull, Path reportPath) {
        private static Options fromSystemProperties() {
            int warmup = positive("warmup", Integer.getInteger("extendedae_plus.cell_benchmark.warmup", 2));
            int measure = positive("measure", Integer.getInteger("extendedae_plus.cell_benchmark.measure", 10));
            int concurrentThreads = positive("concurrentThreads", Integer.getInteger(
                    "extendedae_plus.cell_benchmark.concurrent_threads", DEFAULT_CONCURRENT_THREADS));
            boolean millionFull = Boolean.getBoolean("extendedae_plus.cell_benchmark.million_full");
            String reportProperty = System.getProperty("extendedae_plus.cell_benchmark.report");
            Path report = reportProperty == null || reportProperty.isBlank()
                    ? Path.of("build", "reports", "infinity-storage-cell-comparison.md")
                    : Path.of(reportProperty);
            return new Options(warmup, measure, concurrentThreads, millionFull, report);
        }

        private static int positive(String name, int value) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }

}
