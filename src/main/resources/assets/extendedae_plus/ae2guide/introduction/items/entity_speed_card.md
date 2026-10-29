---
navigation:
  parent: introduction/index.md
  title: Entity Speed Card
  position: 6
  icon: extendedae_plus:entity_speed_card
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:entity_speed_card
---

# Entity Speed Card

<Row>
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':2}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':4}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':8}" scale="3" />
  <ItemImage id="extendedae_plus:entity_speed_card" tag="{'EAS:mult':16}" scale="3" />
</Row>

The **Entity Speed Card** is an upgrade component that can be inserted into the <ItemLink id="extendedae_plus:entity_speed_ticker" /> to provide various levels of acceleration.  
The card’s tier directly determines the **maximum acceleration multiplier limit** that the Entity Accelerator can achieve.

## Card Tiers & Effects

An Entity Accelerator can hold up to **4 speed cards** simultaneously.  
The final acceleration multiplier equals the **product of all inserted cards’ base multipliers**, but it **cannot exceed** the **maximum speed limit** defined by the **highest-tier card** installed.

| Card Type | Base Multiplier | Maximum Speed Limit (per card) |
| :--- | :--- | :--- |
| Entity Speed Card (x2) | ×2 | ×8 |
| Entity Speed Card (x4) | ×4 | ×64 |
| Entity Speed Card (x8) | ×8 | ×256 |
| Entity Speed Card (x16) | ×16 | ×1024 |

> **Mixed Insertion Rule**:  
> When mixing cards of different tiers, the total multiplier is the product of all base multipliers, but the final result **cannot exceed** the **maximum speed limit** of the highest-tier card installed.
>
> **Example**: Insert one **x2 card** and three **x4 cards**.
>
> - Calculated multiplier: 2 × 4³ = 128
> - Highest-tier card: **x4**, with a limit of **×64**
> - **Final acceleration multiplier = ×64**

## Crafting Recipe
<RecipesFor id="extendedae_plus:entity_speed_card" />