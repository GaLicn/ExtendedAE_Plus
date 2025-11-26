---
navigation:
  title: Feature Overview
  parent: introduction/index.md
  position: 0
categories:
  - extendedae_plus features
---

# Mod Feature Overview

## I. Pattern Provider Enhancements

Unless otherwise stated, the following features support **AE2 Pattern Providers** and **ExtendedAE Pattern Providers** only.

### 1. Smart Blocking Mode

Enhanced based on AE2’s original blocking mechanism. When identical pattern materials are continuously dispatched, this prevents waste caused by concurrent recipe execution. (Supports AdvancedAE series providers)

> Requires the original blocking feature to be enabled.

<br/>

### 2. Smart Multiplication Mode

Automatically calculates the total materials required for a crafting order in AE2.  
This mechanism is equivalent to manually duplicating the pattern to the corresponding quantity. (Supports AdvancedAE series providers)

Configurable options include:
- Maximum multiplication limit
- Multi-pattern provider polling dispatch (improves concurrency and distribution efficiency)
- Minimum yield factor

**Example:**

Pattern: 1 Cobblestone + 2 Lava → 1 Stone

When ordering 1000 Stone, the system dispatches 1000 Cobblestone and 2000 Lava at once.

**Additional Configuration:**

Manually set the single-item dispatch limit in the left input box of the pattern provider.

Example: Pattern 1 Cobblestone + 2 Lava → 1 Stone, limit 64; ordering 1000 Stone will dispatch only 32 Cobblestone and 64 Lava at a time.

Supported types: Items, Fluids, Mekanism chemicals.

<br/>

### 3. Manual Multiplication Button

A multiplication control button is added in the pattern provider GUI, allowing players to quickly set pattern multipliers without repeated editing.

---

## II. AE2 Related Features

### 1. Enhanced Quick Operations

- **Middle-click to pull blocks:** Middle-clicking any block will automatically pull it from the AE network if available (requires a wireless terminal).
- **Auto-fill blank patterns:** Opening the Pattern Encoding Terminal automatically fills empty patterns from the AE network into slots.

<br/>

### 2. Crafting Monitoring Interface Enhancements

- **Shift + Click Material:** Quickly opens the corresponding machine interface.
- **Shift + Right-click Material:** Opens the corresponding provider UI and highlights the matching pattern slot. The target provider is also highlighted in the world.
- **Shift + Click Cancel Button:** Automatically adds missing materials to JEI bookmarks.

<br/>

### 3. Pattern Encoding Terminal Enhancements

- **Upload Feature:** Directly upload patterns to the corresponding machine’s pattern provider via the upload button. JEI-pulled recipes detect the machine name and automatically search for matching providers. Automatic search uses a mapping table; mapping keys can be added in the upload interface.
- **Quick Clear:** Right-click the upload search box to clear input quickly.

<br/>

### 4. Pattern Quantity Display & Naming Optimization

- Adds “Pattern Craft Quantity” display in AE2 and ExtendedAE pattern management terminals and providers.
- Pattern provider UI title displays player-customized names.

<br/>

### 5. AE Performance Optimization

- New config option **Crafting Pause Threshold** (default: 100000) adjusts AE’s thread sleep behavior during crafting plan calculation, significantly improving speed for large crafting plans.

<br/>

### 6. Quartz Cutter Function Extension

- **Shift + Right-click Block or AE Component:** Copies its name, supporting copying of sub-recipe names from large GregTech machines.

---

## III. ExtendedAE Enhancements

### 1. Fast Pattern Upload

- **Auto-detect Assembly Matrix:** If an assembly matrix exists in the network, patterns completed in the Pattern Encoding Terminal (for crafting, smelting, or cutting) are automatically uploaded. Existing identical patterns are detected and returned, avoiding duplicates.(The Shift key must be pressed when clicking the encoding button.)  

<br/>

### 2. Configurable Properties

- Extendable pattern provider slot multipliers can be adjusted via the config file.

<br/>

### 3. Terminal Interaction Enhancements

- **Search Highlight:** Adds a search highlight feature to ExtendedAE pattern management terminals (18x18 border + rainbow flow highlight).

---

## IV. Deep JEI & AE2 Integration

### 1. Bidirectional Interaction

- **Shift + Left-click (JEI → AE Network):** Pulls the item directly if available in AE network; if missing, jumps to ordering interface.
> Requires a wireless terminal (can also be in accessory slot).

- **Middle-click JEI item:** If an automated crafting plan exists in AE, jumps to ordering interface.

<br/>

### 2. JEI & Terminal Synchronization

- **F Key:** Syncs the JEI item name to AE2 search bar and ExtendedAE pattern management interface.
- **Pattern Priority Matching:** Prioritize JEI bookmarks when writing patterns.

---

## V. Other Improvements & Utilities

### Pattern Information Display

- Shows author information on completed patterns: `Written by <PlayerName>`