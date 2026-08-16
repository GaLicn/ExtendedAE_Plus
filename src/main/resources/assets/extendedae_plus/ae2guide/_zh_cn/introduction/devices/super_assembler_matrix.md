---
navigation:
  parent: introduction/index.md
  title: 超级装配矩阵
  position: 10
  icon: extendedae_plus:assembler_matrix_hybrid_plus
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:super_assembler_matrix_frame
  - extendedae_plus:super_assembler_matrix_wall
  - extendedae_plus:assembler_matrix_hybrid_plus
  - extendedae_plus:assembler_matrix_upload_core
---

# 超级装配矩阵

<Row>
  <BlockImage id="extendedae_plus:super_assembler_matrix_frame" scale="4" />
  <BlockImage id="extendedae_plus:super_assembler_matrix_wall" scale="4" />
  <BlockImage id="extendedae_plus:assembler_matrix_hybrid_plus" scale="4" />
</Row>

**超级装配矩阵**是一种 AE2 多方块合成供应器。结构成型后，将任意在线的矩阵方块接入 ME 网络，即可通过 AE2 常规自动合成系统调用其中存储的合成样板。

## 结构预览

<GameScene zoom="5" background="transparent" interactive={true}>
  <ImportStructure src="../../structure/super_assembler_matrix.snbt"></ImportStructure>
</GameScene>

## 普通结构

搭建一个边长范围为 **3x3x3 至 9x9x9** 的封闭长方体。

- 所有外框棱边必须使用**超级装配矩阵框架**。
- 外壳上不属于棱边的位置必须使用**超级装配矩阵墙**或 ExtendedAE 装配矩阵玻璃。
- 所有内部位置必须使用**超级装配矩阵混合核心**。
- 每一个内部位置都必须填充混合核心；ExtendedAE 的旧装配矩阵核心不能参与此结构成型。

每个混合核心同时具备一个样板核心、一个合成核心和一个速度核心的效果：提供 **72 个样板槽位**、**512 个合成任务并行**，以及相当于 **5 个普通速度核心**的加速效果。仅可放入 AE2 分子装配器兼容的合成样板。

<Recipe id="extendedae_plus:super_assembler_matrix_frame" />

<Recipe id="extendedae_plus:super_assembler_matrix_wall" />

## 终极结构

**终极超级装配矩阵**是一个固定的 **14x16x14** 结构，精确布局包含 312 个混合核心和 4 个装配矩阵上传核心。

结构成型后提供 **22,464 个样板槽位**，并行合成额度等同于 `2,147,483,647`。建议使用 <ItemLink id="extendedae_plus:ultimate_super_assembler_matrix_builder" /> 放置其精确布局。

> 固定布局中的空气坐标不是结构部件，允许在这些位置放置其他方块，不会阻止终极结构成型。
