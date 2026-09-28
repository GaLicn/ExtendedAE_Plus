---
navigation:
  parent: introduction/index.md
  title: Super Circuit Cutter
  position: 10
  icon: extendedae_plus:circuit_cutter_plus
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:circuit_cutter_plus
---

# Super Circuit Cutter

<BlockImage id="extendedae_plus:circuit_cutter_plus" scale="5" />

The **Super Circuit Cutter** is an accelerated circuit cutter that processes up to **8 copies** of one cutter recipe in each cycle.

## Operation

- Insert the recipe input into the input slot.
- Each cycle automatically determines a parallel count from 1 to 8 according to available input and output space.
- Inputs and AE power are consumed for every parallel operation, and all results are combined in the output slot.
- When output space is limited, only the number of recipes that fit will be processed.

## Power and Upgrades

The machine stores **64,000 AE** and supports up to 4 AE2 Speed Cards. Speed Cards reduce the time per cycle without changing the 8-recipe parallel limit.

Enable **Auto-Export** in the machine interface to send finished products to adjacent inventories.
