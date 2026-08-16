---
navigation:
  parent: introduction/index.md
  title: Super Assembler Matrix
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

# Super Assembler Matrix

<Row>
  <BlockImage id="extendedae_plus:super_assembler_matrix_frame" scale="4" />
  <BlockImage id="extendedae_plus:super_assembler_matrix_wall" scale="4" />
  <BlockImage id="extendedae_plus:assembler_matrix_hybrid_plus" scale="4" />
</Row>

The **Super Assembler Matrix** is a multiblock crafting provider for AE2. After it forms, connect any active matrix block to the ME network and use its stored crafting patterns through the normal AE2 autocrafting system.

## Structure Preview

<GameScene zoom="5" background="transparent" interactive={true}>
  <ImportStructure src="../../structure/super_assembler_matrix.snbt"></ImportStructure>
</GameScene>

## Standard Structure

Build a sealed cuboid from **3x3x3** up to **9x9x9** blocks.

- Every outer edge must use a **Super Assembler Matrix Frame**.
- Every non-edge outer surface block must use a **Super Assembler Matrix Wall** or an ExtendedAE Assembler Matrix Glass block.
- Every internal position must use a **Super Assembler Matrix Hybrid Core**.
- Every internal position must be filled by a Hybrid Core. Original ExtendedAE matrix cores cannot form this structure.

Each Hybrid Core combines the effect of one Pattern Core, one Crafting Core, and one Speed Core: it provides **72 pattern slots**, **512 crafting jobs**, and the speed effect of **five regular Speed Cores**. Only AE2 molecular-assembler compatible crafting patterns can be inserted.

<Recipe id="extendedae_plus:super_assembler_matrix_frame" />

<Recipe id="extendedae_plus:super_assembler_matrix_wall" />

## Ultimate Structure

The **Ultimate Super Assembler Matrix** is a fixed **14x16x14** structure. Its exact layout contains 312 Hybrid Cores and four Assembly Matrix Upload Cores.

When formed, it provides **22,464 pattern slots** and an effectively unlimited parallel crafting budget (`2,147,483,647`). The exact layout is intended to be placed with the <ItemLink id="extendedae_plus:ultimate_super_assembler_matrix_builder" />.

> The empty positions in the fixed layout are not structural parts. They may contain other blocks without preventing the Ultimate Matrix from forming.
