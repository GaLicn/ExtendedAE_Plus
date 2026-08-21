---
navigation:
  parent: introduction/index.md
  title: 标签库存 ME 接口
  position: 7
  icon: extendedae_plus:tag_inventory_me_interface
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:tag_inventory_me_interface
---

# 标签库存 ME 接口

<BlockImage id="extendedae_plus:tag_inventory_me_interface" scale="5" />

**标签库存 ME 接口**会将 ME 网络中符合条件的物品作为物品库存暴露给相邻机器。它不需要逐个设置物品槽，而是通过标签表达式动态筛选物品。

## 配置方式

右键方块可设置白名单与黑名单标签表达式，完成后点击**保存**应用配置。

- `&`：两侧条件必须同时匹配。
- `|`：匹配任意一侧条件即可。
- `^`：两侧条件中必须恰好有一个匹配。
- `!`：对后续条件取反。
- `( )`：对表达式进行分组。
- `*`：在标签名称中作为通配符使用。

例如，`forge:ingots & !forge:ingots/iron` 会选中带有锭标签、但不属于铁锭标签的物品。黑名单会在白名单之后生效，并排除所有匹配的物品。如果两个表达式均为空，接口不会暴露任何物品。

## 自动化行为

- 相邻机器可以直接从 ME 存储中提取符合条件的物品。
- 不能通过该接口向 ME 网络插入物品。
- 当网络中的匹配物品发生变化时，对外暴露的库存会动态更新。
- 方块必须供电并连接到工作的 ME 网络，且会占用一个频道。
- 可以使用兼容 AE2 的扳手拆除该方块。

## 合成配方

<Recipe id="extendedae_plus:tag_inventory_me_interface" />
