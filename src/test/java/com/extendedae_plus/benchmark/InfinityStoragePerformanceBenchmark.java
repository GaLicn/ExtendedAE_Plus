package com.extendedae_plus.benchmark;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 对比吞噬盘存储热路径在优化前后的性能。
 *
 * <p>该基准使用与生产代码相同的 FastUtil Map，并用等价内存模型复现核心算法，不启动 Minecraft。
 * 存储初始化、线程池创建、线程就绪等待和结果校验均不计入测量时间。</p>
 *
 * <p>单盘场景让所有线程在同一个存储对象上通过锁访问，多盘场景让每个线程访问独立存储对象，
 * 避免对生产代码中的非线程安全 Map 进行无锁写入。</p>
 */
public final class InfinityStoragePerformanceBenchmark {
    private static final BigInteger BI_ZERO = BigInteger.ZERO;
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int LONG_MAX_BIT_LENGTH = Long.SIZE - 1;
    private static final long SINK_MASK = 0x5DEECE66DL;

    private final Config config;
    private final BenchKey[] operationKeys;
    private final long[] operationAmounts;
    private final BenchKey[] availableKeys;
    private final BigInteger[] longValues;
    private final BigInteger[] mixedValues;
    private final int bigItemTypes;
    private final List<ConcurrentMeasurement> concurrentMeasurements = new ArrayList<>(8);

    private InfinityStoragePerformanceBenchmark(Config config) {
        this.config = config;
        this.operationKeys = new BenchKey[config.operations()];
        this.operationAmounts = new long[config.operations()];
        this.availableKeys = new BenchKey[config.itemTypes()];
        this.longValues = new BigInteger[config.itemTypes()];
        this.mixedValues = new BigInteger[config.itemTypes()];
        this.bigItemTypes = this.createDeterministicWorkload();
    }

    public static void main(String[] args) {
        Config config = Config.parse(args);
        new InfinityStoragePerformanceBenchmark(config).run();
    }

    private void run() {
        verifyBoundaryCases();

        System.out.println("=== 吞噬盘性能对比测试 ===");
        System.out.printf("并发线程数: %d，物品种类: %d，每线程操作数: %d，大整数位数: %d%n",
                config.threads(), config.itemTypes(), config.operations(), config.bigBits());
        System.out.printf("混合库存中极大数占比: %.2f%%（%d/%d）%n",
                bigItemTypes * 100D / config.itemTypes(), bigItemTypes, config.itemTypes());
        System.out.println("单盘场景：所有线程访问同一个存储对象，并在每次操作外加锁模拟服务端串行访问。");
        System.out.println("多盘场景：每个线程访问独立存储对象，模拟多个吞噬盘同时工作。");
        System.out.printf("预热轮数: %d，测量轮数: %d%n%n", config.warmupRounds(), config.measureRounds());

        compare(
                "单盘共享 %d 线程输入（long 数量）".formatted(config.threads()),
                ConcurrencyGroup.SHARED,
                Operation.INPUT,
                QuantityMode.LONG_ONLY,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, null, true, false),
                () -> prepareConcurrent(AfterStorage::new, null, true, false)
        );

        compare(
                "单盘共享 %d 线程输入（含 BigInteger）".formatted(config.threads()),
                ConcurrencyGroup.SHARED,
                Operation.INPUT,
                QuantityMode.FEW_BIGINTEGER,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, mixedValues, true, false),
                () -> prepareConcurrent(AfterStorage::new, mixedValues, true, false)
        );

        compare(
                "单盘共享 %d 线程输出（long 数量）".formatted(config.threads()),
                ConcurrencyGroup.SHARED,
                Operation.OUTPUT,
                QuantityMode.LONG_ONLY,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, longValues, true, true),
                () -> prepareConcurrent(AfterStorage::new, longValues, true, true)
        );

        compare(
                "单盘共享 %d 线程输出（含 BigInteger）".formatted(config.threads()),
                ConcurrencyGroup.SHARED,
                Operation.OUTPUT,
                QuantityMode.FEW_BIGINTEGER,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, mixedValues, true, true),
                () -> prepareConcurrent(AfterStorage::new, mixedValues, true, true)
        );

        compare(
                "多盘独立 %d 线程输入（long 数量）".formatted(config.threads()),
                ConcurrencyGroup.INDEPENDENT,
                Operation.INPUT,
                QuantityMode.LONG_ONLY,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, null, false, false),
                () -> prepareConcurrent(AfterStorage::new, null, false, false)
        );

        compare(
                "多盘独立 %d 线程输入（含 BigInteger）".formatted(config.threads()),
                ConcurrencyGroup.INDEPENDENT,
                Operation.INPUT,
                QuantityMode.FEW_BIGINTEGER,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, mixedValues, false, false),
                () -> prepareConcurrent(AfterStorage::new, mixedValues, false, false)
        );

        compare(
                "多盘独立 %d 线程输出（long 数量）".formatted(config.threads()),
                ConcurrencyGroup.INDEPENDENT,
                Operation.OUTPUT,
                QuantityMode.LONG_ONLY,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, longValues, false, true),
                () -> prepareConcurrent(AfterStorage::new, longValues, false, true)
        );

        compare(
                "多盘独立 %d 线程输出（含 BigInteger）".formatted(config.threads()),
                ConcurrencyGroup.INDEPENDENT,
                Operation.OUTPUT,
                QuantityMode.FEW_BIGINTEGER,
                (long) config.threads() * config.operations(),
                () -> prepareConcurrent(BeforeStorage::new, mixedValues, false, true),
                () -> prepareConcurrent(AfterStorage::new, mixedValues, false, true)
        );

        System.out.println("测试完成：每轮前后实现的结果均一致。");
        writeReport();

    }

    // 在开始计时前验证 long 与 BigInteger 的提升、降级和总数增量合并。
    private static void verifyBoundaryCases() {
        BenchKey firstKey = new BenchKey(1);
        BenchKey secondKey = new BenchKey(2);
        BeforeStorage before = new BeforeStorage();
        AfterStorage after = new AfterStorage();

        assertSameResult("模拟插入", before.insert(firstKey, 1, true), after.insert(firstKey, 1, true));
        assertSameStorage("模拟插入", before, after);
        assertSameResult("写入 long 最大值",
                before.insert(firstKey, Long.MAX_VALUE, false),
                after.insert(firstKey, Long.MAX_VALUE, false));
        assertSameResult("提升为 BigInteger", before.insert(firstKey, 1, false), after.insert(firstKey, 1, false));
        assertSameStorage("提升为 BigInteger", before, after);
        assertSameResult("降级回 long", before.extract(firstKey, 1), after.extract(firstKey, 1));
        assertSameStorage("降级回 long", before, after);

        // 连续操作期间不读取总数，用于覆盖 BigInteger 基值上的正负 long 增量。
        assertSameResult("写入第二种物品", before.insert(secondKey, 10, false), after.insert(secondKey, 10, false));
        assertSameResult("极大总数增量", before.insert(secondKey, 5, false), after.insert(secondKey, 5, false));
        assertSameResult("移除 long 最大值",
                before.extract(firstKey, Long.MAX_VALUE),
                after.extract(firstKey, Long.MAX_VALUE));
        assertSameStorage("极大总数增量合并", before, after);
        assertSameResult("清空剩余物品", before.extract(secondKey, 15), after.extract(secondKey, 15));
        assertSameStorage("清空剩余物品", before, after);

        // 两次最大 long 增减会越过增量边界，必须先与 BigInteger 基值合并。
        before = new BeforeStorage();
        after = new AfterStorage();
        BigInteger extremeValue = BigInteger.ONE.shiftLeft(256);
        before.seed(new BenchKey[]{firstKey}, new BigInteger[]{extremeValue});
        after.seed(new BenchKey[]{firstKey}, new BigInteger[]{extremeValue});
        assertSameResult("正增量第一次逼近边界",
                before.insert(secondKey, Long.MAX_VALUE, false),
                after.insert(secondKey, Long.MAX_VALUE, false));
        assertSameResult("正增量溢出前合并",
                before.insert(secondKey, Long.MAX_VALUE, false),
                after.insert(secondKey, Long.MAX_VALUE, false));
        assertSameResult("负增量第一次逼近边界",
                before.extract(firstKey, Long.MAX_VALUE),
                after.extract(firstKey, Long.MAX_VALUE));
        assertSameResult("负增量溢出前合并",
                before.extract(firstKey, Long.MAX_VALUE),
                after.extract(firstKey, Long.MAX_VALUE));
        assertSameStorage("增量边界合并", before, after);
    }

    private static void assertSameResult(String stage, long before, long after) {
        if (before != after) {
            throw new AssertionError(stage + " 的操作结果不一致：" + before + " != " + after);
        }
    }

    private static void assertSameStorage(String stage, BenchStorage before, BenchStorage after) {
        WorkResult beforeResult = new WorkResult(before.stateChecksum(), before.total(), before.size());
        WorkResult afterResult = new WorkResult(after.stateChecksum(), after.total(), after.size());
        if (!beforeResult.sameState(afterResult)) {
            throw new AssertionError(stage + " 的库存状态不一致：" + beforeResult + " != " + afterResult);
        }
    }

    private void compare(String name,
                         ConcurrencyGroup group,
                         Operation operation,
                         QuantityMode quantityMode,
                         long operationCount,
                         Supplier<PreparedWork> beforeFactory,
                         Supplier<PreparedWork> afterFactory) {
        for (int i = 0; i < config.warmupRounds(); i++) {
            runSample(beforeFactory);
            runSample(afterFactory);
        }

        long[] beforeSamples = new long[config.measureRounds()];
        long[] afterSamples = new long[config.measureRounds()];
        for (int i = 0; i < config.measureRounds(); i++) {
            Sample before;
            Sample after;
            if ((i & 1) == 0) {
                before = runSample(beforeFactory);
                after = runSample(afterFactory);
            } else {
                after = runSample(afterFactory);
                before = runSample(beforeFactory);
            }

            if (!before.result().sameState(after.result())) {
                throw new AssertionError(name + " 第 " + (i + 1) + " 轮结果不一致："
                        + before.result() + " != " + after.result());
            }
            beforeSamples[i] = before.elapsedNanos();
            afterSamples[i] = after.elapsedNanos();
        }

        long beforeNanos = average(beforeSamples);
        long afterNanos = average(afterSamples);
        double speedup = (double) beforeNanos / afterNanos;
        double beforeThroughput = operationCount * 1_000_000_000D / beforeNanos;
        double afterThroughput = operationCount * 1_000_000_000D / afterNanos;

        System.out.println("[" + name + "]");
        System.out.printf("  优化前: %s，吞吐量: %,.0f 次/秒%n",
                formatDuration(beforeNanos), beforeThroughput);
        System.out.printf("  优化后: %s，吞吐量: %,.0f 次/秒%n",
                formatDuration(afterNanos), afterThroughput);
        System.out.printf("  性能倍率: %.2fx%n%n", speedup);
        concurrentMeasurements.add(new ConcurrentMeasurement(
                name, group, operation, quantityMode, operationCount, beforeNanos, afterNanos, speedup));
    }

    private void writeReport() {
        StringBuilder report = new StringBuilder();
        report.append("# 吞噬盘 FastUtil 并发性能回归报告\n\n");
        report.append("## 测试环境与口径\n\n");
        report.append("- 本测试不启动 Minecraft，使用与生产代码相同的 FastUtil Map，并用等价内存模型对比优化前/优化后的吞噬盘核心算法。\n");
        report.append("- 本次线程数：").append(config.threads()).append("；每线程操作数：").append(config.operations())
                .append("；总操作数：").append((long) config.threads() * config.operations()).append("。\n");
        report.append("- 数据 key 数：").append(config.itemTypes()).append("；其中混合库存默认有 ")
                .append(config.bigPercent()).append("% key 使用 BigInteger（实际 ").append(bigItemTypes).append(" 个）。\n");
        report.append("- ").append(config.warmupRounds()).append(" 轮预热，").append(config.measureRounds())
                .append(" 轮测量，报告算术平均值；初始化、线程池创建、就绪等待、结果校验不计时。\n");
        report.append("- 这里的“性能倍率”定义为 `优化前用时 / 优化后用时`，大于 1 表示优化后更快。\n\n");

        report.append("## 测试用例说明\n\n");
        report.append("### 一、单盘共享并发\n\n");
        report.append("所有线程访问同一个盘；基准通过锁保护每次操作，模拟服务端对同一库存的串行化访问。\n\n");
        report.append("| 用例 | 并发方式 | 数量类型 | 预置库存 | 操作 | 测试目的 |\n");
        report.append("|---|---|---|---|---|---|\n");
        report.append("|单盘共享输入（仅 long）|").append(config.threads()).append(" 个线程|long|空盘|输入|测量一个盘接受高并发输入时的 long 快速路径|\n");
        report.append("|单盘共享输出（仅 long）|").append(config.threads()).append(" 个线程|long|每 key 预置 long 数量|输出|测量一个盘处理高并发输出时的 long 快速路径|\n");
        report.append("|单盘共享输入（少量 BigInteger）|").append(config.threads()).append(" 个线程|混合 long/BigInteger|")
                .append("预置 ").append(config.bigPercent()).append("% BigInteger key|输入|测量预置少量 BigInteger 后继续输入|\n");
        report.append("|单盘共享输出（少量 BigInteger）|").append(config.threads()).append(" 个线程|混合 long/BigInteger|")
                .append("预置 ").append(config.bigPercent()).append("% BigInteger key|输出|测量预置少量 BigInteger 后继续输出|\n\n");

        report.append("### 二、多盘独立并发\n\n");
        report.append("每个线程访问自己的独立盘，不共享库存 Map；用于观察没有共享盘锁竞争时的并发输入/输出开销。\n\n");
        report.append("| 用例 | 并发方式 | 数量类型 | 预置库存 | 操作 | 测试目的 |\n");
        report.append("|---|---|---|---|---|---|\n");
        report.append("|多盘独立输入（仅 long）|").append(config.threads()).append(" 个线程，每线程一个盘|long|空盘|输入|测量多个独立盘进行高并发输入|\n");
        report.append("|多盘独立输出（仅 long）|").append(config.threads()).append(" 个线程，每线程一个盘|long|每盘预置 long 数量|输出|测量多个独立盘进行高并发输出|\n");
        report.append("|多盘独立输入（少量 BigInteger）|").append(config.threads()).append(" 个线程，每线程一个盘|混合 long/BigInteger|")
                .append("每盘预置 ").append(config.bigPercent()).append("% BigInteger key|输入|测量独立盘在混合数量下的高并发输入|\n");
        report.append("|多盘独立输出（少量 BigInteger）|").append(config.threads()).append(" 个线程，每线程一个盘|混合 long/BigInteger|")
                .append("每盘预置 ").append(config.bigPercent()).append("% BigInteger key|输出|测量独立盘在混合数量下的高并发输出|\n\n");

        report.append("## 分组测量结果\n\n");
        appendMeasurementTable(report, ConcurrencyGroup.SHARED, "一、单盘共享并发结果");
        appendMeasurementTable(report, ConcurrencyGroup.INDEPENDENT, "二、多盘独立并发结果");

        report.append("## 结果校验\n\n");
        report.append("每个场景每轮都会比较优化前后的校验和、总数量和 key 数量；所有线程异常都会使测试失败。\n\n");
        report.append("## 总合并对比表\n\n");
        report.append("| 测试分组 | 测试场景 | 总操作数 | 优化前平均值 | 优化后平均值 | 性能倍率 | 优化前 次/秒 | 优化后 次/秒 |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (ConcurrentMeasurement measurement : concurrentMeasurements) {
            report.append('|').append(measurement.group().label).append('|').append(measurement.name()).append('|')
                    .append(measurement.operationCount()).append('|')
                    .append(formatDuration(measurement.beforeNanos())).append('|')
                    .append(formatDuration(measurement.afterNanos())).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", measurement.speedup())).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(measurement.operationCount(), measurement.beforeNanos()))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(measurement.operationCount(), measurement.afterNanos()))).append('|')
                    .append('\n');
        }

        try {
            Path reportPath = Path.of("build", "reports", "infinity-storage-concurrency.md").toAbsolutePath();
            Path parent = reportPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(reportPath, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            System.out.println("并发回归报告: " + reportPath);
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入并发回归报告", exception);
        }
    }

    private void appendMeasurementTable(StringBuilder report, ConcurrencyGroup group, String title) {
        report.append("### ").append(title).append("\n\n");
        report.append("| 测试场景 | 总操作数 | 优化前平均值 | 优化后平均值 | 性能倍率 | 优化前 次/秒 | 优化后 次/秒 |\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (ConcurrentMeasurement measurement : concurrentMeasurements) {
            if (measurement.group() != group) {
                continue;
            }
            report.append('|').append(measurement.name()).append('|').append(measurement.operationCount()).append('|')
                    .append(formatDuration(measurement.beforeNanos())).append('|')
                    .append(formatDuration(measurement.afterNanos())).append('|')
                    .append(String.format(Locale.ROOT, "%.2fx", measurement.speedup())).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(measurement.operationCount(), measurement.beforeNanos()))).append('|')
                    .append(String.format(Locale.ROOT, "%,.0f", throughput(measurement.operationCount(), measurement.afterNanos()))).append('|')
                    .append('\n');
        }
        report.append('\n');
    }

    private Sample runSample(Supplier<PreparedWork> workFactory) {
        PreparedWork work = workFactory.get();
        try {
            long startNanos = System.nanoTime();
            work.execute();
            long elapsedNanos = System.nanoTime() - startNanos;
            WorkResult result = work.result();
            consume(result);
            return new Sample(elapsedNanos, result);
        } finally {
            work.close();
        }
    }

    private PreparedWork prepareConcurrent(Supplier<BenchStorage> storageFactory,
                                           BigInteger[] initialValues,
                                           boolean sharedStorage,
                                           boolean output) {
        int storageCount = sharedStorage ? 1 : config.threads();
        List<BenchStorage> storages = new ArrayList<>(storageCount);
        for (int i = 0; i < storageCount; i++) {
            BenchStorage storage = storageFactory.get();
            if (initialValues != null) {
                storage.seed(availableKeys, initialValues);
            }
            storages.add(storage);
        }

        ExecutorService executor = Executors.newFixedThreadPool(config.threads());
        CountDownLatch ready = new CountDownLatch(config.threads());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(config.threads());
        long[] checksums = new long[config.threads()];
        Throwable[] failures = new Throwable[config.threads()];

        for (int thread = 0; thread < config.threads(); thread++) {
            int threadIndex = thread;
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    BenchStorage storage = storages.get(sharedStorage ? 0 : threadIndex);
                    long checksum = 0;
                    for (int i = 0; i < config.operations(); i++) {
                        BenchKey key = operationKeys[i];
                        long amount = operationAmounts[i];
                        if (sharedStorage) {
                            synchronized (storage) {
                                checksum += output
                                        ? storage.extract(key, amount)
                                        : storage.insert(key, amount, false);
                            }
                        } else if (output) {
                            checksum += storage.extract(key, amount);
                        } else {
                            checksum += storage.insert(key, amount, false);
                        }
                    }
                    checksums[threadIndex] = checksum;
                } catch (Throwable throwable) {
                    failures[threadIndex] = throwable;
                } finally {
                    done.countDown();
                }
            });
        }
        await(ready, "并发测试线程未能完成就绪");

        return new PreparedWork() {
            @Override
            public void execute() {
                start.countDown();
                await(done, "并发测试线程未能在规定时间内结束");
            }

            @Override
            public WorkResult result() {
                long checksum = 0;
                BigInteger total = BI_ZERO;
                int itemCount = 0;
                for (Throwable failure : failures) {
                    if (failure != null) {
                        throw new IllegalStateException("并发测试线程执行失败", failure);
                    }
                }
                for (int i = 0; i < storageCount; i++) {
                    if (sharedStorage) {
                        for (long threadChecksum : checksums) {
                            checksum += threadChecksum;
                        }
                    } else {
                        checksum += checksums[i];
                    }
                    BenchStorage storage = storages.get(i);
                    checksum ^= storage.stateChecksum();
                    total = total.add(storage.total());
                    itemCount += storage.size();
                }
                return new WorkResult(checksum, total, itemCount);
            }

            @Override
            public void close() {
                executor.shutdownNow();
                awaitTermination(executor);
            }
        };
    }

    private int createDeterministicWorkload() {
        BigInteger bigValue = BigInteger.ONE.shiftLeft(config.bigBits()).add(BigInteger.valueOf(123456789L));
        int requestedBigTypes = config.itemTypes() * config.bigPercent() / 100;
        if (config.bigPercent() > 0 && requestedBigTypes == 0) {
            requestedBigTypes = 1;
        }

        for (int i = 0; i < config.itemTypes(); i++) {
            BenchKey key = new BenchKey(i);
            BigInteger longValue = BigInteger.valueOf(1_000_000L + i);
            availableKeys[i] = key;
            longValues[i] = longValue;
            mixedValues[i] = i < requestedBigTypes
                    ? bigValue.add(BigInteger.valueOf(i))
                    : longValue;
        }

        long value = SINK_MASK;
        for (int i = 0; i < config.operations(); i++) {
            value = value * 2_862_933_555_777_941_757L + 3_039_700_493L;
            operationKeys[i] = availableKeys[(int) Long.remainderUnsigned(value, config.itemTypes())];
            operationAmounts[i] = 1L + Long.remainderUnsigned(value >>> 17, 1_024L);
        }
        return requestedBigTypes;
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

    private static void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("基准测试主线程被中断", exception);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                throw new IllegalStateException("并发测试线程池未能关闭");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发测试线程池关闭时被中断", exception);
        }
    }

    private static void consume(WorkResult result) {
        BLACK_HOLE_LONG ^= result.checksum();
        BLACK_HOLE_OBJECT = result.total();
    }

    private static volatile long BLACK_HOLE_LONG;
    private static volatile BigInteger BLACK_HOLE_OBJECT;

    private static String formatDuration(double nanos) {
        if (nanos < 1_000D) {
            return String.format(Locale.ROOT, "%.1f ns", nanos);
        }
        if (nanos < 1_000_000D) {
            return String.format(Locale.ROOT, "%.1f us", nanos / 1_000D);
        }
        if (nanos < 1_000_000_000D) {
            return String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000D);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000D);
    }

    private enum ConcurrencyGroup {
        SHARED("单盘共享"),
        INDEPENDENT("多盘独立");

        private final String label;

        ConcurrencyGroup(String label) {
            this.label = label;
        }
    }

    private enum Operation {
        INPUT,
        OUTPUT
    }

    private enum QuantityMode {
        LONG_ONLY,
        FEW_BIGINTEGER
    }

    private record BenchKey(int id) {
    }

    private record Sample(long elapsedNanos, WorkResult result) {
    }

    private record ConcurrentMeasurement(String name,
                                         ConcurrencyGroup group,
                                         Operation operation,
                                         QuantityMode quantityMode,
                                         long operationCount,
                                         long beforeNanos,
                                         long afterNanos,
                                         double speedup) {
    }

    private record WorkResult(long checksum, BigInteger total, int itemCount) {
        private boolean sameState(WorkResult other) {
            return this.checksum == other.checksum
                    && this.total.equals(other.total)
                    && this.itemCount == other.itemCount;
        }
    }

    private interface PreparedWork extends AutoCloseable {
        void execute();

        WorkResult result();

        @Override
        default void close() {
        }
    }

    private interface BenchStorage {
        long insert(BenchKey key, long amount, boolean simulate);

        long extract(BenchKey key, long amount);

        void seed(BenchKey[] keys, BigInteger[] values);

        void getAvailableStacks(LongCounter out);

        BigInteger total();

        int size();

        long stateChecksum();
    }

    /** 优化前的单 BigInteger Map 算法模型。 */
    private static final class BeforeStorage implements BenchStorage {
        private final Object2ObjectMap<BenchKey, BigInteger> amounts = new Object2ObjectOpenHashMap<>();
        private BigInteger itemCount = BI_ZERO;
        private boolean hasUuid;

        @Override
        public long insert(BenchKey key, long amount, boolean simulate) {
            if (amount == 0) {
                return 0;
            }
            if (!hasUuid) {
                // 优化前，空盘模拟插入也会分配 UUID 和创建持久化条目。
                UUID.randomUUID();
                hasUuid = true;
            }
            if (!simulate) {
                BigInteger currentAmount = amounts.getOrDefault(key, BI_ZERO);
                BigInteger delta = BigInteger.valueOf(amount);
                amounts.put(key, currentAmount.add(delta));
                itemCount = itemCount.add(delta);
            }
            return amount;
        }

        @Override
        public long extract(BenchKey key, long amount) {
            BigInteger currentAmount = amounts.getOrDefault(key, BI_ZERO);
            if (currentAmount.signum() <= 0) {
                return 0;
            }

            BigInteger requested = BigInteger.valueOf(amount);
            if (requested.compareTo(currentAmount) >= 0) {
                amounts.remove(key);
                itemCount = itemCount.subtract(currentAmount);
                return currentAmount.compareTo(BI_LONG_MAX) > 0
                        ? Long.MAX_VALUE
                        : currentAmount.longValue();
            }

            amounts.put(key, currentAmount.subtract(requested));
            itemCount = itemCount.subtract(requested);
            return amount;
        }

        @Override
        public void seed(BenchKey[] keys, BigInteger[] values) {
            for (int i = 0; i < keys.length; i++) {
                amounts.put(keys[i], values[i]);
                itemCount = itemCount.add(values[i]);
            }
            hasUuid = true;
        }

        @Override
        public void getAvailableStacks(LongCounter out) {
            for (var entry : Object2ObjectMaps.fastIterable(amounts)) {
                BenchKey key = entry.getKey();
                BigInteger value = entry.getValue();
                long existing = out.get(key);
                BigInteger sum = BigInteger.valueOf(existing).add(value);
                long toSet = sum.compareTo(BI_LONG_MAX) > 0 ? Long.MAX_VALUE : sum.longValue();
                if (existing != Long.MAX_VALUE) {
                    out.add(key, toSet - existing);
                }
            }
        }

        @Override
        public BigInteger total() {
            return itemCount;
        }

        @Override
        public int size() {
            return amounts.size();
        }

        @Override
        public long stateChecksum() {
            long checksum = 0;
            for (var entry : Object2ObjectMaps.fastIterable(amounts)) {
                checksum += stateEntryChecksum(entry.getKey(), entry.getValue());
            }
            return checksum;
        }
    }

    /** 优化后的 long Map 与 BigInteger Map 双路径算法模型。 */
    private static final class AfterStorage implements BenchStorage {
        private final Object2LongMap<BenchKey> longAmounts = new Object2LongOpenHashMap<>();
        private final Object2ObjectMap<BenchKey, BigInteger> bigAmounts = new Object2ObjectOpenHashMap<>();
        private long longItemCount;
        private BigInteger bigItemCount;
        private boolean hasUuid;

        @Override
        public long insert(BenchKey key, long amount, boolean simulate) {
            if (amount <= 0) {
                return 0;
            }
            if (simulate) {
                return amount;
            }
            if (!hasUuid) {
                UUID.randomUUID();
                hasUuid = true;
            }

            long currentAmount = longAmounts.getOrDefault(key, 0L);
            if (currentAmount > 0) {
                if (amount <= Long.MAX_VALUE - currentAmount) {
                    longAmounts.put(key, currentAmount + amount);
                } else {
                    longAmounts.removeLong(key);
                    bigAmounts.put(key, BigInteger.valueOf(currentAmount).add(BigInteger.valueOf(amount)));
                }
            } else {
                BigInteger currentBigAmount = bigAmounts.get(key);
                if (currentBigAmount == null) {
                    longAmounts.put(key, amount);
                } else {
                    bigAmounts.put(key, currentBigAmount.add(BigInteger.valueOf(amount)));
                }
            }
            addToItemCount(amount);
            return amount;
        }

        @Override
        public long extract(BenchKey key, long amount) {
            if (amount <= 0) {
                return 0;
            }

            long currentAmount = longAmounts.getOrDefault(key, 0L);
            if (currentAmount > 0) {
                long extractedAmount = Math.min(currentAmount, amount);
                if (extractedAmount == currentAmount) {
                    longAmounts.removeLong(key);
                } else {
                    longAmounts.put(key, currentAmount - extractedAmount);
                }
                subtractFromItemCount(extractedAmount);
                return extractedAmount;
            }

            BigInteger currentBigAmount = bigAmounts.get(key);
            if (currentBigAmount == null || currentBigAmount.signum() <= 0) {
                return 0;
            }

            BigInteger removedAmount = amount == Long.MAX_VALUE
                    ? BI_LONG_MAX
                    : BigInteger.valueOf(amount);
            BigInteger remainingAmount = currentBigAmount.subtract(removedAmount);
            if (remainingAmount.signum() <= 0) {
                bigAmounts.remove(key);
            } else if (remainingAmount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                bigAmounts.remove(key);
                longAmounts.put(key, remainingAmount.longValue());
            } else {
                bigAmounts.put(key, remainingAmount);
            }
            subtractFromItemCount(amount);
            return amount;
        }

        @Override
        public void seed(BenchKey[] keys, BigInteger[] values) {
            BigInteger total = BI_ZERO;
            for (int i = 0; i < keys.length; i++) {
                BigInteger value = values[i];
                if (value.bitLength() <= LONG_MAX_BIT_LENGTH) {
                    longAmounts.put(keys[i], value.longValue());
                } else {
                    bigAmounts.put(keys[i], value);
                }
                total = total.add(value);
            }
            setItemCount(total);
            hasUuid = true;
        }

        @Override
        public void getAvailableStacks(LongCounter out) {
            for (var entry : Object2LongMaps.fastIterable(longAmounts)) {
                BenchKey key = entry.getKey();
                long value = entry.getLongValue();
                long existing = out.get(key);
                if (existing == Long.MAX_VALUE) {
                    continue;
                }
                if (existing > Long.MAX_VALUE - value) {
                    out.set(key, Long.MAX_VALUE);
                } else {
                    out.add(key, value);
                }
            }

            for (var entry : Object2ObjectMaps.fastIterable(bigAmounts)) {
                BenchKey key = entry.getKey();
                BigInteger value = entry.getValue();
                long existing = out.get(key);
                if (existing == Long.MAX_VALUE) {
                    continue;
                }
                if (existing >= 0) {
                    out.set(key, Long.MAX_VALUE);
                } else {
                    BigInteger sum = value.add(BigInteger.valueOf(existing));
                    out.set(key, sum.compareTo(BI_LONG_MAX) > 0 ? Long.MAX_VALUE : sum.longValue());
                }
            }
        }

        @Override
        public BigInteger total() {
            if (bigItemCount == null) {
                return BigInteger.valueOf(longItemCount);
            }
            if (longItemCount == 0) {
                return bigItemCount;
            }

            BigInteger itemCount = bigItemCount.add(BigInteger.valueOf(longItemCount));
            if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                setItemCount(itemCount);
            }
            return itemCount;
        }

        @Override
        public int size() {
            return longAmounts.size() + bigAmounts.size();
        }

        @Override
        public long stateChecksum() {
            long checksum = 0;
            for (var entry : Object2LongMaps.fastIterable(longAmounts)) {
                checksum += stateEntryChecksum(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
            }
            for (var entry : Object2ObjectMaps.fastIterable(bigAmounts)) {
                checksum += stateEntryChecksum(entry.getKey(), entry.getValue());
            }
            return checksum;
        }

        private void setItemCount(BigInteger itemCount) {
            if (itemCount.signum() <= 0) {
                longItemCount = 0;
                bigItemCount = null;
            } else if (itemCount.bitLength() <= LONG_MAX_BIT_LENGTH) {
                longItemCount = itemCount.longValue();
                bigItemCount = null;
            } else {
                longItemCount = 0;
                bigItemCount = itemCount;
            }
        }

        private void addToItemCount(long amount) {
            if (bigItemCount != null) {
                if (longItemCount <= Long.MAX_VALUE - amount) {
                    longItemCount += amount;
                } else {
                    bigItemCount = bigItemCount
                            .add(BigInteger.valueOf(longItemCount))
                            .add(BigInteger.valueOf(amount));
                    longItemCount = 0;
                }
            } else if (amount <= Long.MAX_VALUE - longItemCount) {
                longItemCount += amount;
            } else {
                bigItemCount = BigInteger.valueOf(longItemCount).add(BigInteger.valueOf(amount));
                longItemCount = 0;
            }
        }

        private void subtractFromItemCount(long amount) {
            if (bigItemCount != null) {
                if (longItemCount >= Long.MIN_VALUE + amount) {
                    longItemCount -= amount;
                } else {
                    setItemCount(bigItemCount
                            .add(BigInteger.valueOf(longItemCount))
                            .subtract(BigInteger.valueOf(amount)));
                }
            } else {
                longItemCount -= amount;
            }
        }
    }

    private static long stateEntryChecksum(BenchKey key, BigInteger amount) {
        return key.id() * SINK_MASK + amount.hashCode();
    }

    private static final class LongCounter {
        private final Object2LongMap<BenchKey> values = new Object2LongOpenHashMap<>();

        private long get(BenchKey key) {
            return values.getOrDefault(key, 0L);
        }

        private void add(BenchKey key, long amount) {
            values.put(key, get(key) + amount);
        }

        private void set(BenchKey key, long amount) {
            values.put(key, amount);
        }

        private long checksum() {
            long checksum = 0;
            for (var entry : Object2LongMaps.fastIterable(values)) {
                checksum ^= entry.getKey().id() * SINK_MASK + entry.getLongValue();
            }
            return checksum;
        }
    }

    private record Config(int threads,
                          int itemTypes,
                          int operations,
                          int bigBits,
                          int bigPercent,
                          int availableRounds,
                          int warmupRounds,
                          int measureRounds) {
        private static Config parse(String[] args) {
            int processors = Runtime.getRuntime().availableProcessors();
            // 默认使用 128 个线程，覆盖用户实际关心的 128 以上并发场景。
            int threads = Math.max(128, processors);
            int itemTypes = 8_192;
            int operations = 100_000;
            int bigBits = 8_192;
            int bigPercent = 5;
            int availableRounds = 20;
            int warmupRounds = 3;
            int measureRounds = 10;

            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("参数格式必须为 --名称=数值：" + arg);
                }
                String[] parts = arg.substring(2).split("=", 2);
                int value = Integer.parseInt(parts[1]);
                switch (parts[0]) {
                    case "threads" -> threads = requirePositive("threads", value);
                    case "item-types" -> itemTypes = requirePositive("item-types", value);
                    case "operations" -> operations = requirePositive("operations", value);
                    case "big-bits" -> bigBits = requireAtLeast("big-bits", value, 64);
                    case "big-percent" -> bigPercent = requireRange("big-percent", value, 0, 100);
                    case "available-rounds" -> availableRounds = requirePositive("available-rounds", value);
                    case "warmup" -> warmupRounds = requirePositive("warmup", value);
                    case "measure" -> measureRounds = requirePositive("measure", value);
                    default -> throw new IllegalArgumentException("未知参数：" + parts[0]);
                }
            }
            return new Config(threads, itemTypes, operations, bigBits, bigPercent,
                    availableRounds, warmupRounds, measureRounds);
        }

        private static int requirePositive(String name, int value) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " 必须大于 0");
            }
            return value;
        }

        private static int requireAtLeast(String name, int value, int minimum) {
            if (value < minimum) {
                throw new IllegalArgumentException(name + " 必须大于等于 " + minimum);
            }
            return value;
        }

        private static int requireRange(String name, int value, int minimum, int maximum) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " 必须在 " + minimum + " 到 " + maximum + " 之间");
            }
            return value;
        }
    }
}
