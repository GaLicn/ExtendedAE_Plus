---
navigation:
  parent: introduction/index.md
  title: 装配矩阵上传核心
  icon: extendedae_plus:assembler_matrix_upload_core
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:assembler_matrix_upload_core
---

# 装配矩阵上传核心

<BlockImage id="extendedae_plus:assembler_matrix_upload_core" scale="5" />

装配矩阵上传核心是一个功能模块，它为装配矩阵添加了从样板编码终端自动接收并上传样板的能力。

## 功能概述

将此核心安装到装配矩阵后，当您在样板编码终端中编码**合成样板**、**锻造台样板**或**切石机样板**时，终端将能够自动将这些样板上传至装配矩阵的存储中，无需手动转移。

## 版本历史与重要变更

- **版本 1.4.3**：此物品被添加至游戏中。
- **重要变更**：在 **1.4.3 版本之前**，装配矩阵**无需**此核心即具备自动上传上述样板的功能。
- **当前要求**：从 **1.4.3 版本开始**，您必须为装配矩阵安装此核心，才能启用自动上传功能。

> **请注意此次版本变更是破坏性的**。如果您从旧版本更新到 1.4.3 或更高版本，需要为您正在使用的每个装配矩阵手动添加此核心，以恢复自动上传功能。

> **注意**：此核心仅影响从**样板编码终端**到**装配矩阵**的**样板自动上传**功能。它不影响将处理样板上传到**样板供应器**的逻辑。