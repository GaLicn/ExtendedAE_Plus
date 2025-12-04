---
navigation:
  parent: introduction/index.md
  title: 实体加速卡
  position: 6
  icon: extendedae_plus:entity_speed_card
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:entity_speed_card
---

# 实体加速卡

<Row>
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':2}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':4}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':8}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':16}" scale="3" />
</Row>

实体加速卡是一种升级组件，可插入<ItemLink id="extendedae_plus:entity_speed_ticker" />中，为其提供不同级别的加速能力。加速卡的等级直接决定了实体加速器所能达到的**最大加速倍率上限**。

## 加速卡等级与效果

实体加速器最多可同时放入**4张**加速卡。最终的加速倍率为所有插入加速卡的**基础倍率相乘**，但**不能超过**当前装配的**最高等级加速卡**所规定的最大速率上限。

| 加速卡类型 | 基础加速倍率 | 单卡最大速率上限 |
| :--- | :--- | :--- |
| 实体加速卡 (x2) | 2倍 | 8倍 |
| 实体加速卡 (x4) | 4倍 | 64倍 |
| 实体加速卡 (x8) | 8倍 | 256倍 |
| 实体加速卡 (x16) | 16倍 | 1024倍 |

> **混合插入规则**：当插入不同等级的加速卡时，总倍率为所有卡基础倍率的乘积，但最终结果不能超过其中**最高级单卡的最大速率上限**。
>
> **示例**：同时插入1张 **x2卡** 和3张 **x4卡**。
>
> - 计算倍率：2 × 4^3 = 128
> - 最高级单卡为 **x4卡**，其上限为 **64倍**，因此**最终加速倍率为64倍**

## 合成配方
<RecipesFor  id="extendedae_plus:entity_speed_card"/>