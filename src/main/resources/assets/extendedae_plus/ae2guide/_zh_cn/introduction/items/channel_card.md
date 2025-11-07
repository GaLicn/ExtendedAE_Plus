---
navigation:
  parent: introduction/index.md
  title: 频道卡
  position: 2
  icon: extendedae_plus:channel_card
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:channel_card
---

# 频道卡

<ItemImage id="extendedae_plus:channel_card" scale="4" />

频道卡是一种功能升级卡，它将<ItemLink id="extendedae_plus:wireless_transceiver" />的从端功能浓缩为一张卡片。将其安装到支持的AE设备后，该设备即可直接连接到同频率的无线收发器主端，无需使用线缆。

## 功能与使用

### 基本功能
- **无线连接**：插入频道卡的AE设备会自动寻找并连接至**相同频率**的无线收发器主端，从而接入其提供的ME网络频道。
- **调整频率**：
    - **手持频道卡右键**：**增加**卡片自身的频率。
    - **手持频道卡Shift+右键**：**减少**卡片自身的频率。

### 所有权绑定 (v1.4.4+)
- **绑定操作**：手持频道卡**Shift+左键**（无需对准方块），即可将其绑定到你的队伍或个人UUID。
- **绑定要求**：**必须完成绑定后**，频道卡才能成功连接到同样绑定了所有权的无线收发器。

### 支持设备
频道卡可以安装到以下AE设备(及其对应的ExtendedAE扩展版本)中，使其获得无线连接能力。安装后，设备将自动尝试连接至同频率的无线收发器主端。

- <ItemLink id="ae2:interface" />
- <ItemLink id="ae2:import_bus" />
- <ItemLink id="ae2:export_bus" />
- <ItemLink id="ae2:storage_bus" />
- <ItemLink id="ae2:pattern_provider" />

## 工作流程

1.  确保已有一个设置好频率和模式的无线收发器**主端**接入源ME网络。
2.  手持频道卡，通过**Shift+左键**完成所有权绑定。
3.  手持频道卡**右键**或**Shift+右键**调整至所需频率。
4.  将频道卡安装到目标AE设备（如ME接口）中。
5.  该设备将自动连接到同频率且所有权匹配的无线收发器主端。

> **注意**：频道卡与无线收发器主端必须在**频率**和**所有权**（v1.4.4+）上都匹配，才能建立连接。