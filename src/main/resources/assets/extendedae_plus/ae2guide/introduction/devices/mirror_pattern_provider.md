---
navigation:
  parent: introduction/index.md
  title: Mirror Pattern Provider
  position: 4
  icon: extendedae_plus:mirror_pattern_provider
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:mirror_pattern_provider
---

# Mirror Pattern Provider

<BlockImage id="extendedae_plus:mirror_pattern_provider" scale="5" />

The **Mirror Pattern Provider** is a read-only pattern provider that follows another pattern provider as its master. It mirrors the master's patterns and provider settings, allowing the same set of patterns to be exposed at additional locations without manually copying or maintaining duplicate pattern inventories.

## Operating Mechanism

- **Master-following:** After binding, the mirror automatically synchronizes patterns from the selected master pattern provider.
- **Mirrored settings:** Priority, custom name, provider settings, and block push direction are copied from the master when supported.
- **No standalone UI:** The mirror does not provide an independent pattern inventory in terminals. Right-clicking without an item shows its current bound master.
- **Read-only behavior:** Wrenches and memory cards cannot directly modify the mirror. Configure the master provider instead.
- **Supported masters:** Block pattern providers and pattern provider parts can be used as masters. A mirror cannot be used as another mirror's master.

## Binding Method

Use the **Mirror Pattern Binder** <ItemImage id="extendedae_plus:mirror_pattern_binding_tool" scale="1" /> to bind mirrors to a master pattern provider:

- **Shift + Right-click** a master pattern provider to select it.
- **Right-click** an unbound mirror to bind it to the selected master.
- **Right-click** a bound mirror to unbind it.
- After switching the binder to range binding mode, **Shift + Right-click** two points to select a range and bind all mirrors inside that box to the selected master.
- **Shift + Right-click in air** with the binder to clear the stored master and range selection.

## Usage Tips

- Edit patterns and settings on the **master provider**; mirrors will follow automatically.
- Keep the master's chunk loaded. If the master is unavailable, the mirror will keep retrying and resume synchronization when the master can be resolved again.
- If the master is removed while loaded, the mirror clears its binding and mirrored patterns.
- Breaking a mirror does not duplicate the mirrored pattern items.

## Crafting Recipes

### Mirror Pattern Provider
<Recipe id="extendedae_plus:mirror_pattern_provider" />

### Mirror Pattern Binder
<Recipe id="extendedae_plus:mirror_pattern_binding_tool" />
