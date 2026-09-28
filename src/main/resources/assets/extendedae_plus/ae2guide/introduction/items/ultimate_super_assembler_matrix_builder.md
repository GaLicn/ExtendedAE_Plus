---
navigation:
  parent: introduction/index.md
  title: Ultimate Super Assembler Matrix Builder
  position: 13
  icon: extendedae_plus:ultimate_super_assembler_matrix_builder
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:ultimate_super_assembler_matrix_builder
---

# Ultimate Super Assembler Matrix Builder

<ItemImage id="extendedae_plus:ultimate_super_assembler_matrix_builder" scale="4" />

The **Ultimate Super Assembler Matrix Builder** places and immediately forms the fixed Ultimate Super Assembler Matrix structure. It provides a locked preview after an origin has been selected; hold **Ctrl** while previewing to show the individual structure parts.

## Building Workflow

1. Right-click a block face to select the adjacent empty block as the build origin.
2. Inspect the preview. Sneak-right-click another block to replace the selected origin.
3. Right-click a block again to build at the selected origin.
4. Sneak-right-click air to cancel the selected origin without building.

The builder checks every required block position before placing anything. It will not overwrite existing blocks, and a failed placement leaves the world unchanged.

## Creative and Survival Use

In Creative mode, the structure is placed without consuming materials.

For Survival mode, first sneak-right-click an **ME Interface** to bind its ME network. Before building, the tool simulates the full material cost in that network. If all materials are available, it extracts them and places the complete structure. If not, the chat lists every missing item and amount; no materials are consumed.

<Recipe id="extendedae_plus:ultimate_super_assembler_matrix_builder" />

> The tool stores the selected origin and bound ME Interface on itself. Keep the same builder item when continuing a pending placement.

