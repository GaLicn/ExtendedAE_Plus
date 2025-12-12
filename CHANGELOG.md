# Changelog

## [1.5.1]

### Added / 新增
- 标签无线收发器
  - Added Labeled Wireless Transceiver
- JEI 标签中键下单后，若该物品不可自动合成，会自动将物品名称写入 AE 搜索栏
  - When middle-click ordering from JEI and the item cannot be autocrafted, the item name is now written into the AE search bar automatically

### Changed / 变更
- 反转 Shift 上传样板的操作逻辑
  - Reversed the Shift pattern-upload behavior
- 虚拟合成卡改为完成任务而非取消任务，以便收到计划完成通知
  - Virtual Crafting Card now completes the task instead of cancelling it, so completion notifications are delivered

### Fixed / 修复
- 修复虚拟合成卡进入世界时的初始化异常问题
  - Fixed initialization errors when Virtual Crafting Cards load into the world

## [1.5.0]

### Added / 新增
- 新增超级装配矩阵速度核心
  - Added Super Assembler Matrix Speed Core
- 新增超级装配矩阵合成核心
  - Added Super Assembler Matrix Crafting Core
- 新增超级装配矩阵样板核心
  - Added Super Assembler Matrix Pattern Core

### Changed / 变更
- 更替无线收发器材质
  - Updated Wireless Transceiver textures
- 更替样板供应器状态控制器材质
  - Updated Network Pattern Controller textures
- 更替模组 logo
  - Updated mod logo
- 更替 ExtendedAE 依赖版本为最低 1.4.6
  - Raised minimum required ExtendedAE version to 1.4.6

## [1.4.6]

### Added / 新增
- 添加虚拟合成卡，可在供应器升级槽中终止即将完成的合成
  - Added the Virtual Crafting Card, allowing providers to abort near-finished jobs from their upgrade slot
- 为实体加速器提供红石控制模式，支持信号启停
  - Added redstone control support for the Entity Accelerator, enabling signal-based start/stop
- 新增“中间下单”功能，允许直接在合成计划中插入新的订单
  - Added the mid-order feature so new requests can be inserted directly into ongoing crafting plans
- 样板编码终端上传至装配矩阵需按住 Shift 点击编码按钮才会触发
  - Uploading crafting patterns to the Assembler Matrix now only triggers when Shift is held during encoding

### Fixed / 修复
- 修复吞噬盘与 AppFlux 联动配方异常的问题
  - Fixed the Devouring Disk recipe integration issue with AppFlux
- 修复合成计划界面与 ExtendedAE 冲突导致显示异常的问题
  - Fixed visual conflicts between the crafting plan screen and ExtendedAE features

## [1.4.5]

### Added / 新增
- 全新的智能倍增，极端情况下（合成深度 > 100万）不再造成由本模组引起的AE2计算合成树时数据包过大
  - Completely revamped Smart Multiplication, now prevents excessively large data packets caused by this mod when AE2 calculates crafting trees in extreme scenarios (crafting depth > 1 million)
- 添加智能倍增对AAE高级处理样板的支持
  - Added Smart Multiplication support for AAE Advanced Processing Patterns
- 添加单独供应器翻倍限制，可以设置单样物品发配数量上限（具体例子查看GuideME中的介绍）
  - Added individual provider doubling limit, allowing to set the maximum distribution quantity per item type (see the GuideME for specific examples)
- 添加本模组的GuideME介绍
  - Added GuideME introduction for this mod
- 添加上传样板列表自然排序功能
  - Added natural sorting functionality for the upload pattern list
- 添加合成计划字节数显示的格式化功能
  - Added formatted display for crafting plan byte count
- 添加部分物品用于磁盘合成（与AAE、APPFLUX、MEGA均有联动）
  - Add some items for disk synthesis (linked with AAE, APPFLUX, MEGA)

### Changed / 变更
- 调整实体加速器模型
  - Adjusted Entity Accelerator model
- 优化实体加速器性能
  - Optimized Entity Accelerator performance
- 调整接口倍增按钮位置
  - Adjusted the position of the interface multiplication button
- 去除智能倍增最小收益因子配置项
  - Removed the minimum benefit factor configuration option for Smart Multiplication
- 添加对MAE2版本的限制
  - Added version restrictions for MAE2
- 为部分文本添加翻译键支持，改善国际化体验 
  - Added translation key support for some previously hardcoded text, improving internationalization experience
- 略微提升无线收发器基础硬度，锁定状态下挖掘速度降至10%
  - Slightly increased wireless transceiver base hardness, mining speed reduced to 10% when locked 
- 调整部分物品合成配方
  - Adjust the synthesis formula of some items
- 优化本mod磁盘性能
  - Optimize the disk performance of this mod

### Fixed / 修复
- 修复供应器高亮在服务器中不显示的问题
  - Fixed the issue where provider highlighting was not displayed on servers
- 修复上传核心挖掘无掉落物和加速的问题
  - Fixed the issue where the upload core had no drops and acceleration when mined

## [1.4.4]
### Added / 新增
- 实体加速器支持对 AppliedFlux 存储电量的扣除
  - Entity Accelerator now supports deducting energy from AppliedFlux storage.
- 为无线收发器添加已使用频道数的 Jade 显示
  - Added Jade HUD display showing used channel count for Wireless Transceivers.
- 无线收发器支持绑定 FTB Teams，不同队伍将不再共用频率
  - Wireless Transceivers now support FTB Teams binding; different teams no longer share the same frequency.
- 频道卡新增潜行左键操作：绑定 team/uuid（必须绑定，否则无法连接无线收发器）
  - Channel Card: new Shift+LMB operation to bind team/uuid (binding required to connect to Wireless Transceivers).
- 添加使用扳手潜行左键无线收发器，可以快捷地调整频率
  - Added Shift+LMB with wrench on Wireless Transceivers to quickly adjust frequency.

### Changed / 变更
- 优化实体加速器性能与逻辑
  - Optimized Entity Accelerator performance and logic.

### Fixed / 修复
- 修复游戏未添加 JEI 时的崩溃问题
  - Fixed crash when JEI mod is not present.
- 修复选择上传样板供应器时，显示的名称为客户端语言
  - Fixed pattern provider names being displayed in client language when selecting upload targets.

## [1.4.3]
### ⚠️ 重要提醒 / Important Notice
- **更新会导致旧版的无限盘失效，如有需要请先导盘再更新**
  - **Updating will invalidate old infinite disks. Please export data before updating if needed.**

### Added / 新增
- 装配矩阵上传核心 - 现在必须在装配矩阵中添加该核心才能正常使用自动上传合成样板功能
  - Assembly Matrix Upload Core - now required in Assembly Matrix to use automatic pattern upload functionality.
- 频道卡 - 手持频道卡右键可调整频率，将频道卡放入AE设备将自动连接上同频率的本模组的无线收发器
  - Channel Card - right-click while holding to adjust frequency, placing in AE devices automatically connects to same-frequency wireless transceivers.

### Fixed / 修复
- 修复合成计划中添加缺失物品时，流体、MEK化学品出现错误书签
  - Fixed incorrect bookmarks for fluids and Mekanism chemicals when adding missing items in crafting plan.
- 修复吞噬万籁的寂静的重大bug
  - Fixed critical bug in Devourer of Cosmic Silence.
- 修复JEI模组不存在时导致的崩溃问题
  - Fixed crash when JEI mod is not present.

## [1.4.2]
### Added / 新增
- 添加实体加速器，最高可加速 1024 倍（配置文件可配置能耗、黑名单、额外消耗倍率名单）
  - Added Entity Accelerator block, capable of up to 1024× acceleration (configurable energy cost, blacklist, and extra consumption multiplier list).
- 添加实体加速卡系列，用于设置实体加速器加速倍率
  - Added Entity Acceleration Card series to configure acceleration multiplier for the Entity Accelerator.
- 添加物品“吞噬万籁的寂静”，可存储 21 亿种不同的资源，每种资源存储数量无上限（气体等物品须安装对应附属 mod）
  - Added item “Devourer of Cosmic Silence”: stores up to 2.1 billion distinct resources with unlimited quantity per type (gases and other types require respective addon mods).
- eae扩展样板管理界面添加F键搜索支持
  - Added F-key search support in Extended Pattern Management Terminal.
- 合成计划界面支持 Shift+点击取消自动添加缺失材料至 JEI 书签
  - Crafting Plan GUI: Shift-clicking cancel button auto-adds missing ingredients to JEI bookmarks.
- AE 合成暂停检查阈值配置项（默认值 100000）
  - Added config option for AE crafting pause threshold (default: 100000).
- 智能系列支持高级 AE 供应器
  - Smart series now supports Advanced AE Providers.
- 无线收发器支持重命名，Jade 可以在从节点显示主节点名称
  - Wireless Repeater supports renaming; Jade HUD displays master node name on slave terminals.
- 智能阻挡开启时自动启用原版阻挡
  - Enabling Smart Blocking now also activates Vanilla Blocking automatically.
- 调整样板制作数量显示上限
  - Adjusted display limit for pattern output quantity.
- 放宽扩展供应器样板手动倍增限制
  - Relaxed manual doubling restrictions for Extended Providers.
- 为ME接口添加倍增按钮用于倍增标记物品数量
  - Add a multiplier button to the ME interface for multiplying the marked item quantity
### Fixed / 修复
- 修复无线收发器频道限制与渲染问题
  - Fixed channel limit and rendering issues for Wireless Repeater.

## [1.4.1]
### Added / 新增
- 模组配置项：可设置智能倍增的最大倍数  
  - Config option: set maximum multiplier for Smart Doubling.
- 上传样板搜索框：右键点击可清空文本  
  - Right-clicking the pattern upload search box clears the text.
- 合成监控界面：打开样板供应器 UI 时自动跳转到该样板所在页数，并以彩虹高亮显示  
  - Crafting monitor: auto-jumps to the page containing the pattern and highlights it with rainbow effect when opening the provider UI.
- 样板供应器 UI 标题现在显示为玩家自定义名称  
  - Pattern Provider UI now displays the player-customized name as its title.
- 配置项：当产物数量达到指定值时启用智能倍增  
  - Config option: enable Smart Doubling only when output amount reaches a specified threshold.

### Fixed / 修复
- 修复编码终端中空白配方无法覆盖已有编码样板的问题  
  - Fixed issue where blank recipes couldn't overwrite existing encoded patterns in the Encoding Terminal.
- 修复非 AE 与 ExtendedAE 样板供应器无法发配材料的问题  
  - Fixed issue where non-AE2 and non-ExtendedAE pattern providers failed to dispatch materials.

## [1.4.0-fix]
### Added / 新增
- JEI 书签优先用于编码样板匹配（书签越靠前，匹配优先级越高）
  - Use JEI bookmarks as primary hints for pattern encoding; earlier bookmarks have higher priority.
- 可配置：在编码样板上显示“由 <玩家名> 编写”
  - Configurable: show "Encoded by <player>" on encoded patterns.
- 可配置：智能翻倍模式支持轮询发配，提高多供应器/多目标的分摊与并发效率
  - Configurable: round-robin dispatch in Smart Doubling to improve distribution/concurrency across providers/targets.
- 内存卡：支持复制“智能阻挡”和“智能翻倍”相关设置
  - Memory Card: can copy settings related to Smart Block and Smart Doubling.
- 添加映射后，自动将本次新增的中文名写入搜索栏并立即应用筛选
  - After adding a mapping, auto-fill the newly added Chinese name into the search bar and apply filtering.
- 可配置：控制样板管理终端默认隐藏/显示槽位渲染
  - Configurable: control default slot rendering visibility in the Pattern Management Terminal.

### Fixed / 修复
- 修复模型材质渲染异常问题
  - Fixed an issue with model/texture rendering artifacts.

## [1.4.0]
### Added / 新增
- 添加智能倍增模式：一次性发配够需求量，降低后期下单卡顿
  - Added Smart Doubling mode: dispatch enough items upfront to reduce late-game order lag.
- 增加模组配置 UI 界面，可配置相关属性
  - Added in-game config UI for adjustable properties.
- 供应器支持启用/禁用智能倍增
  - Providers can enable/disable Smart Doubling.
- 扩展样板管理终端中添加按钮，可打开样板供应器对应机器的 UI 界面
  - Added a button in the Extended Pattern Management Terminal to open the provider's target machine UI.
- 无线收发器适配 Jade 显示信息
  - Wireless terminals now integrate with Jade HUD.
- 合成进度界面：Shift+左键跳转到对应机器 UI，Shift+右键跳转到对应供应器 UI
  - Crafting progress view: Shift+LMB jumps to machine UI; Shift+RMB to provider UI.
- 新增样板供应器状态控制器方块，连接到 ME 网络后可全局调整所有样板供应器三个模式的启用状态
  - Added a Provider State Controller block to toggle three modes globally when connected to the ME network.

## [1.3.3]
### Added / 新增
- ME 扩展样板管理终端：搜索命中槽位增加 18x18 边框与彩虹流转高亮
  - Added 18x18 border and rainbow highlight for search-hit slots in the Extended Pattern Terminal.
- 装配矩阵：新增锻造台与切石机配方上传
  - Assembler Matrix: added upload support for Smithing Table and Stonecutter recipes.
- 在 AE2 与 ExtendedAE 的终端与供应器中显示样板生产数量
  - Show pattern output amounts in AE2 and ExtendedAE terminals/providers.
- 增加并行处理单元系列，最高 1024 并行
  - New Parallel Processing Units, up to 1024-way parallelism.
- 新增：JEI 书签界面按 F 键自动搜索物品到 AE 终端
  - New: press F on JEI bookmarks to auto-search the item in the AE terminal.

### Fixed / 修复
- 修复同方块多样板供应器贴片误选供应器问题
  - Fixed provider mis-selection on blocks with multiple provider attachments.
- 修复多人游戏下智能阻挡按钮属性值显示不同步
  - Fixed desync of Smart Block button state in multiplayer.

### Docs / 文档
- 重要：与 modern ae2 additions 模组不兼容（加装后智能阻挡无效）
  - Important: incompatible with "modern ae2 additions" (Smart Block becomes ineffective when installed together).

## [1.3.2]
### Added / 新增
- 增加映射修改便捷功能（输入名称即可删除并重新添加）
  - Added quick mapping edit: re-add by typing the name to replace.
- 供应器新增高级阻挡模式：相同配方不再阻挡，直至该配方完成清空容器后再发配下一种（支持输入总成；总成内电路不受影响；催化剂槽内不可放置任意一个样板的输入物品）
  - Advanced Block mode for providers: do not block identical recipes; dispatch the next recipe only after the current one completes and the container is cleared (supports input assemblies; internal circuits unaffected; catalyst slot cannot accept inputs for any pattern).

### Fixed / 修复
- 修复石英切割刀 Shift+右键复制名称功能，且支持格雷大型机器不同配方类型子名称
  - Fixed Quartz Cutting Knife Shift+RMB name-copying; supports sub-names for different GT large machine recipe types.

### Docs / 文档
- 与 modernae 模组存在兼容性问题：同时安装不报错，但高级阻挡模式会失效
  - Compatibility issue with "modernae": no crash together, but Advanced Block mode becomes ineffective.
- 高级阻挡模式需同时开启“原版阻挡模式”和“高级阻挡模式”方可生效
  - Advanced Block mode requires both Vanilla Block Mode and Advanced Block Mode to be enabled.

## [1.3.1]
### Added / 新增
- 无线收发器支持：放下后默认从端、频率 1；支持镐子挖掘；支持 AE2 扳手 Shift+右键快速拆除；扳手右键可锁定/解锁收发器（锁定后无法更改主从与频道）
  - Wireless Repeater support: default slave and channel 1 when placed; mineable by pickaxe; AE2 wrench Shift+RMB to quickly break; wrench RMB to lock/unlock (locking prevents changing role/channel).
- 石英切割刀：Shift+右键实体可复制其名称到剪切板，便于命名样板供应器
  - Quartz Cutting Knife: Shift+RMB an entity to copy its name to clipboard for naming pattern providers.

### Fixed / 修复
- 修复 JEI 作弊模式下 Shift+左键无法获取物品的问题
  - Fixed inability to obtain items with Shift+LMB in JEI cheat mode.
- 修复同名供应器中其中一个满了不会上传到其他的问题（建议打开“未满且可见”模式）
  - Fixed issue where uploads wouldn't go to other providers if one with the same name was full (recommend enabling "Not Full & Visible" mode).

### Changed / 变更
- 无线收发器默认行为与交互方式调整（见 Added）
  - Adjusted default behavior and interactions for the Wireless Repeater (see Added).

## [1.3.0]
### Added / 新增
- 为处理样板添加快速上传功能
  - Added quick upload for processing patterns.
- 自动搜索改为采用映射表实现
  - Auto-search now implemented via a mapping table.

### Docs / 文档
- 可在 `/config/extendedae-plus/recipe_type_names.json` 修改映射名称
  - You can edit mapping names at `/config/extendedae-plus/recipe_type_names.json`.

## [1.2.2]
### Added / 新增
- JEI 中 Shift+左键自动拉取物品到背包；若 AE 中没有但可自动化合成，则跳转到下单界面
  - In JEI, Shift+LMB auto-pulls items to inventory; if not stored but craftable, jumps to the order screen.
- 上传样板到装配矩阵：新增已有样板检测；已有则不再上传，并在左下角提示玩家
  - Uploading patterns to the Assembler Matrix now checks existing ones; skips upload and notifies the player if already present.

### Fixed / 修复
- 修复上传样板到装配矩阵偶发失败的问题
  - Fixed occasional failures when uploading patterns to the Assembler Matrix.
- 修复一个可能引发偶发崩端的问题
  - Fixed an issue that could occasionally cause client crashes.

## [1.2.1-fix]
### Added / 新增
- 编码样板终端：编写合成样板时自动上传到装配矩阵（网络需有装配矩阵）
  - Pattern Encoding Terminal: auto-upload crafting patterns to the Assembler Matrix (requires it on the network).
- 鼠标中键点击某方块：若 AE 网络有库存，自动拉取到手上
  - Middle-click a block to pull it to hand if the AE network has stock.
- JEI 中对着物品鼠标中键：若网络有该物品的自动化方案，自动跳转到下单界面
  - In JEI, middle-clicking an item jumps to the order screen if a crafting plan exists.
- 支持在 Curios 槽位使用通用终端；量子卡可随时随地进行下单与拉取
  - Universal Terminal works in Curios slots; Quantum Card enables ordering/pulling anywhere.

### Changed / 变更
- 优化交互逻辑（减少奇怪的交互问题）
  - Improved interaction logic (reduces odd interaction issues).

### Docs / 文档
- 上述“中键拉取/跳转”功能需携带无线终端（放饰品槽也支持）
  - The middle-click pull/jump features require carrying a Wireless Terminal (Curios slot supported).

## [1.2.0-a]
### Added / 新增
- 新增无线收发器方块：可无线传输 ME 频道，支持跨维度（主方块区块需设置强加载）
  - Added Wireless Repeater block: transmits ME channels wirelessly across dimensions (keep the main block chunk-loaded).
- 为无线收发器添加抗卸载机制（重启后无需手动更新主端状态）
  - Added unload-resilience for the Wireless Repeater (no manual master-state refresh after restart).

### Changed / 变更
- 更改扩展样板管理终端：打开 UI 时默认隐藏所有样板
  - Extended Pattern Terminal: hide all patterns by default when opening the UI.

### Fixed / 修复
- 修复 JEI 无法正常搜索到物品的问题
  - Fixed an issue where JEI could not properly search for items.

### Performance / 性能
- 提升网络稳定性：无线收发器抗卸载机制
  - Improved network stability via the Wireless Repeater's unload-resilience.

### Docs / 文档
- 操作方法：空手右键切换主从；Shift+右键频道+1；Shift+左键频道-1；主端发信，从端收信
  - How-to: bare-hand RMB to toggle master/slave; Shift+RMB channel +1; Shift+LMB channel -1; master transmits, slave receives.

## [1.1.3-b]
### Added / 新增
- 配置项 `pageMultiplier`
  - Config option `pageMultiplier`.
- 打开样板编码终端时自动填充空白样板
  - Auto-fill blank patterns when opening the Pattern Encoding Terminal.

### Fixed / 修复
- 修复其他 AE 物品 UI 显示异常
  - Fixed display issues in other AE item UIs.
