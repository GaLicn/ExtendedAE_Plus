---
navigation:
  parent: introduction/index.md
  title: Assembler Matrix Upload Core
  position: 8
  icon: extendedae_plus:assembler_matrix_upload_core
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:assembler_matrix_upload_core
---

# Assembler Matrix Upload Core

<BlockImage id="extendedae_plus:assembler_matrix_upload_core" scale="5" />

The **Assembler Matrix Upload Core** is a functional module that adds the ability for an Assembler Matrix to automatically receive and upload patterns from the Pattern Encoding Terminal.

## Feature Overview

When installed in an Assembler Matrix, patterns created in the Pattern Encoding Terminal — including **crafting patterns**, **smelter patterns**, or **stonecutter patterns** — are automatically uploaded into the Assembler Matrix storage without manual transfer.

## Version History & Important Changes

- **Version 1.4.3**: This item was added to the game.
- **Important Change**: Prior to **version 1.4.3**, Assembler Matrices **did not require** this core to automatically upload the above patterns.
- **Current Requirement**: Starting from **version 1.4.3**, you must install this core in an Assembler Matrix to enable automatic upload.

> **Warning:** This is a breaking change. When updating from older versions to 1.4.3 or higher, you need to manually install this core in each Assembler Matrix you use to restore automatic upload functionality.

> **Note:** This core only affects the **automatic upload of patterns from the Pattern Encoding Terminal to the Assembler Matrix**. It does not affect the logic for uploading processing patterns to **Pattern Providers**.
