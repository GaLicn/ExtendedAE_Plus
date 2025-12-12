---
navigation:
  parent: introduction/index.md
  title: 标签无线收发器
  position: 2
  icon: extendedae_plus:labeled_wireless_transceiver
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:labeled_wireless_transceiver
---

# 标签无线收发器

<BlockImage id="extendedae_plus:labeled_wireless_transceiver" scale="5" />

标签无线收发器基于无线收发器的跨维度传输能力，新增“标签网络”：通过输入标签自动生成/绑定唯一频道，同标签设备可直接互联，无需手动调频道数字。

## 工作机制

- **标签 → 频道映射：** 输入标签后，从标签注册表获取或创建唯一频道，并自动更新自身频率。
- **多点共享：** 多个同标签设备加入同一频道，实现一对多连接
- **绑定隔离：** 标签记录放置者/队伍信息，不同玩家或队伍的同名标签不会互通，确保隔离。
- **跨维度支持：** 与普通无线收发器一致，支持跨维度；主从区块需保持加载。

## 操作指南

### 标签管理

- **输入/搜索框：** 左侧输入标签可即时过滤列表。
- **新建标签（New）：** 用输入框内容注册标签并绑定当前设备。
-- **设为当前（Set）：** 选择列表标签后切换本设备频道到该标签。
- **删除（Delete）：** 选中标签后删除（仅对自己拥有/有权限的标签有效）。
- **断开（Disconnect）：** 清除当前标签并断开无线连接。

### 信息面板

右侧信息区显示：
- 当前标签与所有者
- 在线设备数量
- 频道占用（已用/最大）

