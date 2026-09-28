---
navigation:
  parent: introduction/index.md
  title: Labeled Wireless Transceiver
  position: 2
  icon: extendedae_plus:labeled_wireless_transceiver
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:labeled_wireless_transceiver
---

# Labeled Wireless Transceiver

<BlockImage id="extendedae_plus:labeled_wireless_transceiver" scale="5" />

The **Labeled Wireless Transceiver** builds on the Wireless Transceiver’s cross-dimensional channel transmission and adds a **label network**: you bind a text label to auto-generate a unique channel, so devices sharing the same label connect without manual frequency tuning.

## Operating Mechanism

- **Label → Channel mapping:** Enter a label to register or fetch a unique channel from the label registry; the device updates its frequency automatically.
- **Shared channels:** Multiple devices with the same label join the same channel, enabling one-to-many or many-to-many links.
- **Ownership isolation:** Labels remember the placer (or team). Same-name labels from different players/teams do not connect, keeping channels isolated.
- **Cross-dimensional:** Same as the Wireless Transceiver—cross-dim is supported; keep both chunks loaded.

## Operation Guide

### Label management

- **Input/Search box:** Type a label on the left to filter the list.
- **New:** Click **New** to register the typed label and bind this device to it.
- **Set:** Select a label in the list and click **Set** to switch this device to that channel.
- **Delete:** Select a label and click **Delete** to remove it from the registry (only for labels you own/have permission for).
- **Disconnect:** Click **Disconnect** to clear the current label and break the wireless link.

### Info panel

The right panel shows:
- Current label & owner
- Online device count
- Channel usage (used / max)
