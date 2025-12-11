# Labeled Wireless Transceiver 设计方案（标签无线收发器）

## 目标与命名
- 新方块/物品/BE：Labeled Wireless Transceiver（标签无线收发器），注册名 `labeled_wireless_transceiver`。
- 功能与旧收发器一致，但增加 UI，使用字符串标签管理频道；底层仍用 long 频率。
- 旧收发器保留原行为，频段互不干扰。

## 交互与 UI（无主从，统一标签界面，虚拟节点中心）
- 方块交互：**取消全部徒手/道具交互，只保留右键打开 UI**。
- 单一界面，核心元素：
  1) 全局标签-频道列表（从服务端获取 LabelNetworkRegistry 映射），展示标签名、频道号、在线端点数。
  2) 当前收发器的标签与频道号（只读频道号，标签可编辑/选择）。
  3) 连接状态：已连接、未连接、离线/超距（可用状态徽标）。
  4) 操作按钮：
     - “新建标签”：输入合法标签，分配频道并创建/获取对应虚拟节点，当前收发器加入该网络。
     - “删除标签”：删除当前标签映射（当网络端点数为 0 时销毁虚拟节点并回收频道），当前收发器清空标签并断开。
     - “设为当前频道”：将当前收发器切换到列表选中的标签网络（连接到该标签的虚拟节点）。
     - “刷新列表”：从服务端重新获取映射与统计。
  5) 搜索/过滤框（可选）：按标签关键字过滤列表。
- UI 流程（建议）：
  - 打开 UI → 获取全局映射列表 + 当前端点的标签/频道 + 连接状态 → 填充列表和回显。
  - 应用/切换标签：发送 C2S 包（BlockPos + label + 操作类型），服务端分配/查询频道号、创建或获取虚拟节点并写回 BE。
  - 删除：发送删除操作；服务端移除映射（若网络无端点则销毁虚拟节点，回收频道），将该 BE 频率清零；列表与回显刷新。

## 标签网络注册中心（虚拟节点中心）
- 新建 `LabelNetworkRegistry`（SavedData/服务端单例）管理标签网络：
  - Key：`(label标准化, 维度或 null 取决于跨维配置, owner/team UUID)`
  - Value：`LabelNetwork`：`Set<EndpointRef>` 端点集合、`VirtualNodeRef virtualNode`（虚拟 AE2 节点句柄）、`long channel`（专用频道号）、在线统计。
- 虚拟节点：
  - 创建：新建/首用标签时，使用 `ManagedGridNode`，`setInWorldNode(false)`，`create(level, null)` 创建非 in-world 节点；`setIdlePowerUsage(0)`（可配置）并设置 visual representation。
  - 维度选择：若跨维开启，统一用主世界；否则按所在维度创建对应虚拟节点。
  - 持久化：SavedData 记录虚拟节点需要的重建信息（labelKey、channel、owner/team）；重启时重建虚拟节点并复用频道。
- 连接拓扑：
  - 所有收发器端点直接连接到该标签的虚拟节点（单中心，避免选举和 n² 连接）。
  - 当标签网络端点数为 0 时，销毁虚拟节点并回收频道号。
- 频道号分配：
  - 预留专用频率区间：从 `1_000_000` 起向上分配，防止与旧版收发器冲突。
  - 频道号存放在 LabelNetwork 中，端点读取后设置自己的 `frequency` 字段即可，无需改底层 AE2 API。
- API 建议：
  - `register(endpoint, label, owner, level)`：加入网络，若无虚拟节点则创建并分配频道。
  - `unregister(endpoint)`：移除端点，若网络端点为 0 则销毁虚拟节点并回收频道。
  - `setLabel(endpoint, newLabel)`：先注销再按新标签注册。
  - `listNetworks(owner, dim/null)`：供 UI 拉取“标签-频道-在线数”。
  - `getNetwork(labelKey)`：UI 获取当前标签网络的在线端/状态。
- 持久化：SavedData 持久化标签→频道号→端点集合（弱引用/位置）及虚拟节点重建所需信息；主线程访问，定期清理无效引用。
- 校验：字符集 `[A-Za-z0-9_-]`，长度 ≤ 32（或 64），标准化 trim + lower，空串无效。

## BE 与逻辑复用
- BE 保留 `long frequency` 字段与节点/连接逻辑，不分主从；连接目标是标签对应的虚拟节点。
- 新增字段：`String labelForDisplay`（UI 回显）；`long frequency` 由标签网络分配。
- NBT：继续存 `frequency`（long）和 `label` 字符串；加载后通过 registry 获取频道号并连向虚拟节点。
- 连接实现：可复用 `WirelessSlaveLink` 的“连接到指定节点”能力，去掉主端假设；或实现单一 `LabelLink`，始终尝试连接虚拟节点（失败则重试/断开）。

## 网络与数据包
- 新包：`LabelNetworkActionC2SPacket`（BlockPos + label + 操作类型[新建/删除/切换]）。
- 服务端处理：
  1) 校验 label → 调用 `LabelNetworkRegistry` 分配/切换/删除；必要时创建/销毁虚拟节点。
  2) 写入 BE：更新 `frequency`、`labelForDisplay`；指向虚拟节点并重连。
  3) 反馈消息包含标签、频道号、在线数/连接状态。

## 方块与资源
- 方块模型/贴图/语言键需新增（`block.extendedae_plus.labeled_wireless_transceiver` 等）。
- 配方独立（避免与旧版冲突）。
- BlockState：可复用旧收发器的 STATE 显示逻辑。

## 配置项（可选）
- 映射区间起始/上限。
- 标签字符集/长度限制。
- 跨维度共享开关（与现有无线配置保持一致）。

## 风险与注意点
- 并发分配：务必在服务端单点分配并二次检查注册中心占用，防止重复。
- 同名稳定：同 owner/team 的同名需返回同一频道号，否则可能导致已有网络断开。
- 预留区间耗尽：需提示玩家清理无用标签或回退使用旧版数值收发器。
- 交互收缩：已移除所有徒手 +/- 频率操作，玩家需通过 UI 设置标签。
