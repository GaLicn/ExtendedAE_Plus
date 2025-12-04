---
navigation:
  parent: introduction/index.md
  title: Virtual Crafting Card
  position: 3
  icon: extendedae_plus:virtual_crafting_card
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:virtual_crafting_card
---

# Virtual Crafting Card

<Row>
  <ItemImage id="extendedae_plus:virtual_crafting_card" scale="4" />
</Row>

The Virtual Crafting Card is a specialized upgrade for Pattern Providers that instantly completes the "final dispatch" for tasks where the products don't need to be returned to storage.

## Features and Effects

- **Auto-Complete**: Instantly marks a task as complete when the provider is about to dispatch the final batch and the recipe doesn't require returning products to the ME network.
- **Queue Release**: Frees up the crafting memory immediately without waiting for product return, allowing for subsequent scheduling.
- **Single Card Limit**: Only 1 card can be installed per Pattern Provider (including extended versions).

## Usage Scenarios

| Scenario | Effect |
| :--- | :--- |
| Multiblock Construction | Materials are automatically marked as delivered once dispatched to the target location, no need to wait for return confirmation |
| Manual Crafting Aid | Non-automatable materials (like Nether Stars, enchanted books, etc.) are dispatched to a chest near the crafting table, automatically completing the task after manual collection |
| Bulk Block Placement | Directly dispatches large quantities of building blocks to the hotbar, auto-completing after placement |
| Automated Production Line Supply | Automatically completes after replenishing consumables (like furnace fuel, experience bottles, etc.) for automated production lines |

## Supported Devices

- <ItemLink id="ae2:pattern_provider" />
- <ItemLink id="ae2:pattern_provider_part" />
- <ItemLink id="extendedae_plus:ex_pattern_provider" />
- <ItemLink id="extendedae_plus:ex_pattern_provider_part" />

## Crafting Recipe

<RecipesFor id="extendedae_plus:virtual_crafting_card" />
