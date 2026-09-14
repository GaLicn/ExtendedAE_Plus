# 吞噬盘、Omni BigInteger 盘与 DE 三位一体：测试用例与复现指南

本文面向希望自行运行或审阅三方性能测试的开发者。按下文运行一次基准命令，即可让三个方案的每个用例分别预热 2 次、正式测量 10 次，并生成中文平均耗时报告；随后可从报告生成 PNG 图片。

编写日期：2026-09-14。本文按当前 `TrinityStorageComparisonBenchmark.java` 的实际实现说明；已有耗时和倍率见 `build/reports/trinity-storage-comparison/trinity-storage-comparison.md`。

## 1. 测试对象和范围

| 方案 | 实际调用对象 | 初始化方式 |
|---|---|---|
| 当前吞噬盘 | `InfinityBigIntegerCellInventory` | 每个样本创建独立 `InfinityStorageManager` 和真实 `StorageCell` |
| Omni BigInteger 盘 | `AEBigIntegerCellInventory` | 使用 Omni 1.1.6 JAR；创建空 `AEBigIntegerCellData` 和对应库存，物品为 `creative_ae_cell_biginteger` |
| DE 三位一体（Trinity） | `TrinityDataCoreStorageSavedData` | 使用 DE 3.2.2 JAR；每个后端创建独立 SavedData 对象和 UUID，输入使用 `TrinityDataCoreStorageProfile.UNLIMITED` |

三方都接收相同的 `AEKey` 和单次 `long` 请求数量。DE 测量的是三位一体存储后端，不包含多方块搭建、容量组件、信息交换仓和网络挂载的完整运行成本。所有方案均不测存档落盘、网络传输和客户端渲染，吞噬盘和 Omni 的保存回调为空实现。

本套测试的 Omni 列始终是 **BigInteger 盘**，包括名为“long 数量级”的用例；这里的 long 指请求和库存数量范围。旧吞噬盘与 Omni long 盘属于原有的另一套基准。

“AE 终端读取”测量 `getAvailableStacks` / `addAvailableStacks` 生成库存列表的耗时，不会启动客户端或实际打开 GUI，因此不能当作玩家点击终端到画面显示的总时间。

## 2. 环境和依赖

以下命令以 Windows PowerShell 为例，工作目录必须是包含 `gradlew.bat` 的项目根目录。源码目录可放在任意位置，不需要沿用原作者的绝对路径。

| 项目 | 当前配置 / 要求 |
|---|---|
| Java | JDK 21；此前运行使用 Azul Zulu 21.0.9 |
| Gradle | 项目 Wrapper 8.14.3，无需单独安装 Gradle |
| Minecraft / NeoForge | 1.21.1 / 21.1.216 |
| AE2 | 19.2.17，由 Gradle 解析 |
| ExtendedAE Plus | 当前源码，模组版本 1.6.1；对外复现应同时记录 Git 提交和工作区差异 |
| Omni | `libs/ae2omnicells-1.1.6-1.21.1-neoforge.jar` |
| Data Energistics | `libs/data_energistics-1.21.1-3.2.2.jar`，直接使用预编译 JAR，无需构建 DE 源码 |
| LDLib2 | 2.2.38.a，DE 的运行依赖，由 Gradle 从 Modrinth 获取 |
| 基准 JVM | `-Xms1G -Xmx4G -XX:+UseSerialGC` |
| 绘图 | 可选；Python 3.10 或以上、Matplotlib、NumPy、中文字体 |

需要完整项目和 `libs` 依赖，单独复制 Java 测试类无法完成 NeoForge 注册和启动。其余模组依赖由 `build.gradle` 决定；`libs/*.jar` 也会被加入运行环境。首次运行需联网下载 Gradle、Minecraft 和模组依赖，缓存齐全后才能使用 `--offline`。

百万 key 的数据构造、预置和校验也会占用内存，即使它们不计入报告耗时。请为基准 JVM、Gradle 和操作系统留出可用内存；对比双方的机器、JVM 参数、模组和配置应保持一致。

本文核对的两个 JAR 的 SHA-256 如下，可用于确认同名文件是否是同一份构建：

```text
data_energistics-1.21.1-3.2.2.jar
C070A38D11B63CE67376FACFC635A005785A38F9AA24A858ACB6DDFA392EF7EA

ae2omnicells-1.1.6-1.21.1-neoforge.jar
9CED732D2140D103DF8D675A1986940395C15C82DC1BFD14BF1F45E2C68A2FC1
```

## 3. 运行默认的 10 次平均测试

### 3.1 设置 Java 和工作目录

把示例路径替换成自己的安装位置；已正确设置 JDK 21 的终端可省略 `JAVA_HOME` 赋值。

```powershell
Set-Location 'D:\Projects\ExtendedAE_Plus_1.21'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version

Get-Item .\libs\ae2omnicells-1.1.6-1.21.1-neoforge.jar
Get-Item .\libs\data_energistics-1.21.1-3.2.2.jar
```

不要直接运行该 Java 类的 `main`：基准入口是服务器启动事件，需要 Minecraft 注册表和测试物品完成初始化。也不要使用 `gradlew test` 代替下方专用任务。

### 3.2 正式运行并保留控制台输出

```powershell
$benchmarkOutput = Join-Path (Get-Location) 'build\reports\trinity-storage-comparison'
New-Item -ItemType Directory -Path $benchmarkOutput -Force | Out-Null
$benchmarkStarted = Get-Date
$benchmarkLog = Join-Path $benchmarkOutput ('console-' + $benchmarkStarted.ToString('yyyyMMdd-HHmmss') + '.log')

.\gradlew.bat runTrinityStorageComparisonBenchmark --no-daemon --no-configuration-cache --console=plain -PtrinityBenchmarkWarmup=2 -PtrinityBenchmarkMeasure=10 -PtrinityBenchmarkConcurrentThreads=128 -PtrinityBenchmarkMillionFull=false 2>&1 | Tee-Object -FilePath $benchmarkLog
$benchmarkExitCode = $LASTEXITCODE
```

专用任务会自动编译本项目、启动 NeoForge 服务器、注册 4,096 种普通测试物品和一种用于 NBT 变体的物品、执行基准、写入报告，然后关闭服务器。运行目录为 `run/trinity-storage-comparison`；首次启动可能需要创建测试世界。默认监听端口为 25565，如被占用，可在该运行目录的 `server.properties` 中设置其他 `server-port` 后重试。本地复现可同时设置 `server-ip=127.0.0.1`。

若启动明确提示需要接受 Minecraft EULA，请阅读并自行决定是否接受，再按提示修改运行目录内的 `eula.txt`。以实际启动提示为准。

**运行上面的 Gradle 命令一次就已经包含每个用例、每个方案的 10 次正式测量。** 这 10 次在同一个基准 JVM 内执行，每次都重新创建后端和预置数据；不是把同一块盘连续操作 10 次，也不是启动 10 个独立 JVM。

### 3.3 判断是否真正成功

当前启动入口会捕获基准线程异常并关闭服务器，内部测试失败时仍可能显示 `BUILD SUCCESSFUL`。因此不能只检查 Gradle 退出码，也不能把上次遗留的报告当成本次结果。

紧接着在同一 PowerShell 窗口执行：

```powershell
$benchmarkReport = Join-Path $benchmarkOutput 'trinity-storage-comparison.md'
if ($benchmarkExitCode -ne 0) {
    throw "Gradle 运行失败，检查 $benchmarkLog"
}
if (-not (Test-Path -LiteralPath $benchmarkReport)) {
    throw "没有生成基准报告，检查 $benchmarkLog"
}
if ((Get-Item -LiteralPath $benchmarkReport).LastWriteTime -lt $benchmarkStarted) {
    throw '报告没有在本次运行中更新'
}
if (Select-String -LiteralPath $benchmarkLog -Pattern 'Trinity storage comparison benchmark failed' -Quiet) {
    throw "基准内部失败，检查 $benchmarkLog"
}
Get-Item -LiteralPath $benchmarkReport | Select-Object FullName, LastWriteTime, Length
Get-Content -LiteralPath $benchmarkReport -Encoding UTF8 | Select-Object -First 22
```

再确认控制台出现 `三方对比报告:`，报告标注“预热 2 轮，正式测量 10 轮”。默认总合并表应有 **23 条结果**，含 8 条并发结果和 1 条百万 key 读取结果；开启完整百万测试后应为 **27 条**。

控制台通过 `Tee-Object` 保存；服务器另有 `run/trinity-storage-comparison/logs/latest.log` 和 `debug.log`。逐用例的 `System.out` 输出以保存的控制台日志为准。每次成功重跑会覆盖同路径 Markdown 报告，重画会覆盖同名 PNG，分享前请归档对应的一套文件。

## 4. 参数和其他运行方式

| Gradle 参数 | 默认值 | 含义 |
|---|---|---|
| `-PtrinityBenchmarkWarmup=2` | 2 | 每个用例、每个方案预热次数；当前必须大于 0 |
| `-PtrinityBenchmarkMeasure=10` | 10 | 每个用例、每个方案正式测量次数；当前必须大于 0 |
| `-PtrinityBenchmarkConcurrentThreads=128` | 128 | 并发用例线程数；独立盘数量随之变化，普通用例仍为单线程 |
| `-PtrinityBenchmarkMillionFull=true` | false | 增加百万 key 的模拟/实际输入、模拟/实际输出，共 4 个用例 |
| `-PtrinityBenchmarkReport=...` | `build/reports/trinity-storage-comparison/trinity-storage-comparison.md` | 报告路径；相对路径以项目根目录为基准 |
| `--offline` | 不启用 | 只用本地依赖缓存；首次准备环境时不要加 |

缓存齐全后的默认测试：

```powershell
.\gradlew.bat runTrinityStorageComparisonBenchmark --no-daemon --no-configuration-cache --offline
```

完整百万 key 测试，仍然每个方案预热 2 次、正式 10 次，报告写到另一个文件以保留默认结果：

```powershell
.\gradlew.bat runTrinityStorageComparisonBenchmark --no-daemon --no-configuration-cache --offline -PtrinityBenchmarkMillionFull=true -PtrinityBenchmarkMeasure=10 '-PtrinityBenchmarkReport=build/reports/trinity-storage-comparison/trinity-storage-comparison-million-full.md'
```

上述简写命令未保留控制台文件，需要日志时沿用第 3 节的 `Tee-Object` 写法。自定义报告路径时，相应修改成功检查中的 `$benchmarkReport`。目前没有跳过百万 key 构造和默认读取的参数，也没有逐条筛选用例的参数。

## 5. 数据集、数量和计时口径

### 5.1 数据定义

| 名称 | key 构成 | 数量 / 请求 |
|---|---|---|
| 标准数据集 S | 4,096 个不同注册物品的普通 key，接着 4,096 个同物品的 NBT 变体，共 8,192 个 key | 普通预置 `N = 1,000,000`；每 key 请求 `a[i]` 为固定算法生成的 1～1,024 |
| long 数量数据集 L | 与 S 共享相同 key 和顺序 | 每 key 请求/预置 `L = Long.MAX_VALUE / 8192 = 1,125,899,906,842,623` |
| 全 BigInteger 数据集 B | 与 S 相同 | 每 key 先输入 `Long.MAX_VALUE`，再输入 1，得到 `B = Long.MAX_VALUE + 1 = 9,223,372,036,854,775,808`；计时请求仍为 `a[i]` |
| 百万数据集 M | 同一物品的 1,000,000 个 NBT 变体，编号 0～999,999 | 读取/输出预置每 key 为 N；输入/输出请求每 key 为 1 |
| 并发数据集 C | 从 S 每隔 32 个取一个，共 256 个 key（128 普通 + 128 NBT） | 每线程按相同顺序访问全部 256 个 key 一次；请求 `c[i]` 为另一固定种子生成的 1～1,024 |

NBT 变体只设置 `CUSTOM_DATA.benchmark_variant` 这一个整数。测试没有生成复杂嵌套 NBT、附魔列表或不可堆叠装备，不能据此证明大量复杂附魔物品打开终端的端到端性能。

并发“少量 BigInteger”的实际预置是：每盘所有 key 先存 N，再对前 `floor(256 × 5 / 100) = 12` 个 key 输入 `Long.MAX_VALUE` 和 1。因此每盘 12 个 key 为 `B + N`，244 个 key 为 N；比例是 **4.6875%**。默认选中的这 12 个 key 均来自普通物品子集。这里的“仅 long”只表示数量落在 long 范围；Omni 和 DE 仍可能内部使用 BigInteger 保存。

### 5.2 计时和顺序

| 项目 | 计时范围 |
|---|---|
| 单线程输入/输出 | 一个批次的循环、后端调用和返回数量累加；标准批次 8,192 次，百万批次 1,000,000 次 |
| 列表读取 | 创建一个空 `KeyCounter` 并调用一次库存枚举接口；吞吐量按枚举 key 数计算 |
| 并发输入/输出 | 所有工作线程就绪后，从释放开始信号到等待全部完成，包含唤醒、调度、循环、实际操作和共享盘锁竞争 |
| 不计入单个样本耗时 | 测试 key 生成、建盘、预置、线程池创建、开始前的就绪等待、计时后的汇总和校验 |

自动报告中“同步等待不计时”的表述不够精确：代码把并发开始信号释放和完成等待纳入了计时。阅读并发结果时应以上表为准。

每个用例依次完成预热和正式测量；三方循环轮换执行顺序，不同时运行三方的计时样本。10 次的三个起始位置不完全等频，轮换只用于减轻顺序偏差。预热和正式测量都在同一 JVM 中，后台服务器仍在运行。2 次预热不保证所有短路径都已充分 JIT 编译，测试也不隔离操作系统调度、CPU 频率和 GC。

## 6. 逐项测试用例

下表“预期状态”由用例的输入输出规则推导，供审阅用途；当前程序的实际自动校验范围见第 7 节。

### 6.1 标准 8,192 key：单线程，5 个用例

| 编号 / 报告用例 | 计时前预置 | 计时内操作 | 预期最终状态 |
|---|---|---|---|
| S1 输入 SIMULATE | 空盘 | 对 S 的每 key 模拟输入 `a[i]` | 返回可接收数量，仍为空盘，0 key |
| S2 输入 MODULATE | 空盘 | 对 S 的每 key 实际输入 `a[i]` | 每 key 为 `a[i]`，8,192 key |
| S3 输出 SIMULATE | 每 key 为 N | 对每 key 模拟输出 `a[i]` | 返回可取数量，库存仍为 N，8,192 key |
| S4 输出 MODULATE | 每 key 为 N | 对每 key 实际输出 `a[i]` | 每 key 为 `N - a[i]`，8,192 key |
| S5 AE 终端读取 | 每 key 为 N | 一次列表枚举 | 库存不变，列表包含 8,192 key |

### 6.2 long 数量级：单线程，5 个用例

| 编号 / 报告用例 | 计时前预置 | 计时内操作 | 预期最终状态 |
|---|---|---|---|
| L1 long 数量输入 SIMULATE | 空盘 | 每 key 模拟输入 L | 库存不变，0 key |
| L2 long 数量输入 MODULATE | 空盘 | 每 key 实际输入 L | 每 key 为 L，8,192 key |
| L3 long 数量输出 SIMULATE | 每 key 为 L | 每 key 模拟输出 L | 库存不变，8,192 key |
| L4 long 数量输出 MODULATE | 每 key 为 L | 每 key 实际输出 L | 所有 key 清空，0 key |
| L5 long 数量终端读取 | 每 key 为 L | 一次列表枚举 | 库存不变，8,192 key |

这里是全盘总量 `8192 × L = Long.MAX_VALUE - 8191` 接近 long 上限，单个 key 的数量约为 `1.126 × 10^15`。L4 包含条目移除/清空成本，S4 只扣减数量，两者不是相同操作状态。

### 6.3 全 BigInteger 预置：单线程，4 个用例

| 编号 / 报告用例 | 计时前预置 | 计时内操作 | 预期最终状态 |
|---|---|---|---|
| B1 BigInteger 预置后继续输入 | 每 key 为 B | 每 key 实际输入 `a[i]` | 每 key 为 `B + a[i]`，仍超过 long，8,192 key |
| B2 BigInteger 预置后模拟输出 | 每 key 为 B | 每 key 模拟输出 `a[i]` | 库存不变，8,192 key |
| B3 BigInteger 预置后真实输出 | 每 key 为 B | 每 key 实际输出 `a[i]` | 每 key 为 `B - a[i]`，回到 long 范围，8,192 key |
| B4 BigInteger 预置后终端读取 | 每 key 为 B | 一次列表枚举 | 每 key 对 AE2 显示为 `Long.MAX_VALUE`，库存不变 |

B3 会触发吞噬盘的 BigInteger → long 存储迁移，不能将其解释成“扣减后仍保持 BigInteger 的稳态性能”。并发混合数量输出 C4/C8 因额外预置了 N，默认操作后那 12 个 key 仍超过 long 上限。当前没有单独的“BigInteger 预置后输入 SIMULATE”用例。

### 6.4 百万 key：默认 1 个，完整模式增加 4 个

| 编号 / 报告用例 | 是否默认运行 | 计时前预置 | 计时内操作 | 预期最终状态 |
|---|---|---|---|---|
| M1 百万 key AE 终端读取 | 是 | M 每 key 为 N | 一次列表枚举 | 库存不变，1,000,000 key |
| M2 百万 key 输入 SIMULATE | 否 | 空盘 | 每 key 模拟输入 1 | 空盘，0 key |
| M3 百万 key 输入 MODULATE | 否 | 空盘 | 每 key 实际输入 1 | 每 key 为 1，1,000,000 key |
| M4 百万 key 输出 SIMULATE | 否 | M 每 key 为 N | 每 key 模拟输出 1 | 库存不变，1,000,000 key |
| M5 百万 key 输出 MODULATE | 否 | M 每 key 为 N | 每 key 实际输出 1 | 每 key 为 999,999，1,000,000 key |

### 6.5 单盘共享：128 线程，4 个用例

每线程访问 C 中 256 个 key，各操作一次；共 `128 × 256 = 32,768` 次实际输入或输出。每次后端调用外部统一使用 `synchronized (backend)`，同一时刻只有一个线程进入共享后端。

| 编号 / 报告用例 | 盘数 | 预置 | 计时内操作 | 默认预期最终数量（每 key） |
|---|---:|---|---|---|
| C1 单盘共享 128 线程输入（仅 long） | 1 | 空盘 | 所有线程实际输入 `c[i]` | `128 × c[i]` |
| C2 单盘共享 128 线程输出（仅 long） | 1 | 每 key 为 N | 所有线程实际输出 `c[i]` | `N - 128 × c[i]` |
| C3 单盘共享 128 线程输入（少量 BigInteger） | 1 | 12 key 为 `B + N`，244 key 为 N | 所有线程实际输入 `c[i]` | 对应预置量 `+ 128 × c[i]` |
| C4 单盘共享 128 线程输出（少量 BigInteger） | 1 | 同 C3 | 所有线程实际输出 `c[i]` | 对应预置量 `- 128 × c[i]` |

四个用例最终均有 256 key。此组比较的是锁保护下的共享盘竞争和吞吐量，不证明后端原生线程安全，也不等于 AE2 正常游戏中有 128 个线程同时修改一块盘。

### 6.6 多盘独立：128 线程，4 个用例

每线程拥有一个独立后端，并访问相同的 256 个 key 一次，共 128 个后端、32,768 次操作。没有额外的跨盘共享锁。DE 组为 128 个独立 SavedData 对象，各自一个 UUID，不是同一世界 SavedData 中的 128 个 host。

| 编号 / 报告用例 | 盘数 | 每盘预置 | 每线程操作 | 每盘最终数量（每 key） |
|---|---:|---|---|---|
| C5 多盘独立 128 线程输入（仅 long） | 128 | 空盘 | 实际输入 `c[i]` | `c[i]` |
| C6 多盘独立 128 线程输出（仅 long） | 128 | 每 key 为 N | 实际输出 `c[i]` | `N - c[i]` |
| C7 多盘独立 128 线程输入（少量 BigInteger） | 128 | 12 key 为 `B + N`，244 key 为 N | 实际输入 `c[i]` | 对应预置量 `+ c[i]` |
| C8 多盘独立 128 线程输出（少量 BigInteger） | 128 | 同 C7 | 实际输出 `c[i]` | 对应预置量 `- c[i]` |

每盘最终均有 256 key。报告“结果 key”是所有盘合并后的不同 key 数，因此仍为 256；不能据此认为只测试了一个盘。以上并发预期按默认 128 线程给出，修改线程数后共享盘的累计操作次数和可能的库存耗尽情况也会变化。

## 7. 自动校验与结果含义

预置通过实际 `insert` 建立，并检查每次预置返回量等于请求量。正式测量的每轮，程序比较三方的操作返回总量、终端可见总量、不同 key 数、校验和及空/非空状态，并检查吞噬盘的跨轮最终摘要是否稳定。预热样本同样会运行预置和结果汇总，但没有正式轮中的三方摘要一致性断言。

多盘用例在计时结束后先分别读取各盘到空 `KeyCounter`，再由基准统一按 key 饱和合并：同 key 总量超过 long 时显示为 `Long.MAX_VALUE`。该步骤不计入并发操作耗时，避免校验结果受三方各自的“向已有列表累加”行为影响。当前合并函数跳过非正条目。

这些校验属于结果摘要校验：没有逐 key 的独立参考模型，也没有比较超 long 库存的精确 BigInteger 余量。数量饱和后，部分内部差异可能不会反映在摘要中；枚举用例计时后的校验还会再次读取列表。测试通过应表述为“本套摘要校验通过”，不能当作完整的数据正确性或线程安全证明。

| 报告字段 | 解释 |
|---|---|
| 平均耗时 | 10 次正式样本耗时的算术平均；是整批操作或一次枚举的时间，不是单次 insert 的时间 |
| 操作数 | 标准 8,192，百万 1,000,000，并发默认 32,768；读取行指枚举条目数 |
| 次/秒 | 操作数 ÷ 平均耗时（秒）；读取行可理解为 key/秒 |
| 吞噬盘/Omni | Omni 平均耗时 ÷ 吞噬盘平均耗时，即吞噬盘是 Omni 性能的多少倍 |
| 吞噬盘/DE | DE 平均耗时 ÷ 吞噬盘平均耗时，即吞噬盘是 DE 性能的多少倍 |
| Omni/DE | DE 平均耗时 ÷ Omni 平均耗时，即 Omni 是 DE 性能的多少倍 |
| 结果 key | 最终列表的不同 key 数，多盘行已经合并同 key |

例如 `2.00x` 表示同批操作中吞噬盘速度为对方的 2 倍、耗时约为对方的一半；`0.75x` 表示速度为对方的 75%、耗时约为对方的 1.33 倍。倍率由程序在耗时文本四舍五入前计算，手算已显示的两位小数耗时可能有小偏差。

源码编译产物和外部 JAR 都由当前 JVM 执行字节码，JAR 并不意味着每次调用都读取压缩包，也不是网络调用。但三方接口检查、保存通知和内部存储结构存在区别，不能因为“同一 JVM”就声称所有成本完全等价。当前测试是固定工作负载的工程对比，不是 JMH 多进程隔离测试，也未输出逐轮原始样本、标准差或置信区间。特别短的模拟路径和高并发样本容易受 JIT、调度及 GC 影响，不宜把一次报告的倍率当作恒定结论。

## 8. 生成并查看图片

默认报告通过成功检查后，在项目根目录执行：

```powershell
python --version
python -m pip install matplotlib numpy
python tools/plot_trinity_storage_comparison.py
```

脚本直接读取已经生成的 Markdown，总合并表默认应解析出 23 条结果，不会重新运行 Java 基准。不需要 Pandas。Windows 中文字体优先使用 `Deng.ttf`，其次 `simhei.ttf`、`simsun.ttc`；其他系统需要在脚本 `choose_font()` 中配置可用中文字体。

| 输出文件（均位于 `build/reports/trinity-storage-comparison/`） | 内容 |
|---|---|
| `trinity-storage-comparison.md` | Java 基准生成的中文结果、分组表和总合并表 |
| `trinity-storage-comparison-table.png` | 总合并表图片 |
| `trinity-storage-comparison-ratio.png` | 吞噬盘相对 Omni/DE 的性能倍数，1x 为等速 |
| `trinity-storage-comparison-latency.png` | 三方平均耗时对数图，底层时间单位为秒 |
| `trinity-storage-benchmark-guide.md` | 本指南的分享副本 |

当前绘图脚本的输入 `SOURCE`、输出 `OUTPUT` 是固定路径，没有命令行参数；如需绘制自定义报告，应先把脚本中的这两个常量改为对应路径。耗时图标题目前固定写“10 次正式测量平均”，改变正式次数时须同步修改标题，避免误标。当前字体组合可能出现对数刻度的负号缺字警告；对外分享前应检查刻度显示，可在绘图脚本中改用普通时间单位刻度标签或支持该字形的字体。

## 9. 常见问题

| 现象 | 检查和处理 |
|---|---|
| `JAVA_HOME` 无效 / Java 版本不符 | 指向本机真实 JDK 21 目录；检查 `gradlew.bat --version` |
| 离线模式提示缺少依赖 | 去掉 `--offline` 联网运行，完成缓存后再离线 |
| 找不到 DE / Omni 类或模组 | 确认第 2 节指定的两个 JAR 存在、版本匹配，并保留完整 Gradle 依赖配置 |
| 提示缺少 `ldlib2` | 使用当前 `build.gradle` 中的 LDLib2 运行依赖并允许首次下载 |
| `Benchmark items are not registered` | 使用 `runTrinityStorageComparisonBenchmark` 专用任务，确保 Trinity 开关生效，不要同时启用旧 cell 基准开关 |
| 端口绑定失败 | 关闭占用同端口的测试服务，或修改专用运行目录内的 `server-port` |
| 构建成功却没新报告 | 查保存的控制台日志和 `latest.log` 中的 `Trinity storage comparison benchmark failed`；按第 3.3 节检查时间戳 |
| `结果不一致` / `预置失败` | 保存完整异常和环境，先核对代码、JAR、参数；不要通过删除断言来获取性能报告 |
| 百万阶段耗时较长 / 内存不足 | 预置和枚举会多次创建大表；检查可用内存、页面文件及后台负载；更改堆大小后要对三个方案整体重测并记录参数 |
| 生成图片找不到报告 / 图片仍是旧数据 | 确认本次报告已更新；自定义输出路径需同步绘图脚本的 `SOURCE` |

## 10. 对外分享时附带什么

请将本指南、同次运行的 Markdown 报告、PNG、保存的控制台日志放在一起，并记录运行命令、操作系统、CPU、内存、JDK、JVM 参数、Git 提交和修改状态、模组列表及配置。这样接收者可以区分代码变化、环境变化和测量波动。

在项目根目录可用下列只读命令采集部分复现信息：

```powershell
git rev-parse HEAD
git status --short
.\gradlew.bat --version
Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory
Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version
Get-FileHash .\libs\data_energistics-1.21.1-3.2.2.jar, .\libs\ae2omnicells-1.1.6-1.21.1-neoforge.jar -Algorithm SHA256 | Format-List
```

分享源码时以 `docs/trinity-storage-benchmark-guide.md` 为维护版本；`build/reports` 下的文件属于生成物，可能被 `gradlew clean` 删除，应在清理前另行归档。

代码定位（相对于项目根目录）：

| 文件 | 用途 |
|---|---|
| `src/main/java/com/extendedae_plus/api/storage/TrinityStorageComparisonBenchmark.java` | 数据生成、用例、预热/测量、汇总校验、报告输出 |
| `src/main/java/com/extendedae_plus/ExtendedAEPlus.java` | 服务器启动后的基准入口和结束处理 |
| `src/main/java/com/extendedae_plus/init/ModItems.java` | 基准模式专用测试物品注册 |
| `build.gradle` | 专用任务、系统属性、堆参数和依赖 |
| `tools/plot_trinity_storage_comparison.py` | 从报告生成三张中文 PNG |

本指南没有引入新的测试样本或重算既有性能结果。默认执行的 23 个用例为 S1～S5、L1～L5、B1～B4、M1、C1～C8；M2～M5 必须显式开启完整百万测试。
