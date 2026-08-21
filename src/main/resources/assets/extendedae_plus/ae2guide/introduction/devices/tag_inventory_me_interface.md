---
navigation:
  parent: introduction/index.md
  title: Tag Inventory ME Interface
  position: 7
  icon: extendedae_plus:tag_inventory_me_interface
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:tag_inventory_me_interface
---

# Tag Inventory ME Interface

<BlockImage id="extendedae_plus:tag_inventory_me_interface" scale="5" />

The **Tag Inventory ME Interface** exposes selected items from an ME network as an item inventory for adjacent machines. Instead of configuring individual item slots, it selects items dynamically using tag expressions.

## Configuration

Right-click the block to configure its whitelist and blacklist tag expressions, then select **Save** to apply them.

- `&` requires both conditions to match.
- `|` accepts either condition.
- `^` accepts exactly one of the two conditions.
- `!` negates the following condition.
- `( )` groups parts of an expression.
- `*` can be used as a wildcard in tag names.

For example, `c:ingots & !c:ingots/iron` selects tagged ingots other than iron ingots. The blacklist is applied after the whitelist and excludes any matching items. If both expressions are empty, the interface exposes no items.

## Automation Behavior

- Adjacent machines can extract matching items directly from ME storage.
- Items cannot be inserted into the ME network through this interface.
- The exposed inventory updates dynamically as matching network contents change.
- The block must be powered and connected to an active ME network, and it consumes one channel.
- Use an AE2-compatible wrench to remove the block while preserving normal wrench behavior.

## Crafting Recipe

<Recipe id="extendedae_plus:tag_inventory_me_interface" />
