---
navigation:
  parent: introduction/index.md
  title: Wireless Transceiver
  position: 1
  icon: extendedae_plus:wireless_transceiver
categories:
  - extendedae_plus devices
item_ids:
  - extendedae_plus:wireless_transceiver
---

# Wireless Transceiver

<BlockImage id="extendedae_plus:wireless_transceiver" scale="5" />

The **Wireless Transceiver** is an advanced wireless AE device that combines the convenience of wireless connectors with the cross-dimensional capabilities of Quantum Rings, allowing ME network channels to be transmitted wirelessly to multiple locations.

## Operating Mechanism

### Basic Concept
The Wireless Transceiver uses a **master-slave** architecture:
- **Master:** Connected to the source ME network providing the channel.
- **Slave:** Placed where the channel is needed, receiving it from a master. **One master can serve multiple slaves.**

### Cross-Dimensional Transmission
Supports cross-dimensional channel transmission.  
**Requirement:** Both master and slave chunks must remain loaded.

## Operation Guide

### Mode & Frequency Settings

- **Right-click empty hand:** Toggle the device's **Master/Slave mode**.
- **Shift + Right-click:** **Increase** the device’s pairing frequency.
- **Shift + Left-click:** **Decrease** the device’s pairing frequency.
- **Hold Wrench + Left-click:** Quickly adjust the transceiver’s frequency.
- **Wrench + Right-click:** **Lock** the current mode and frequency to prevent accidental changes.
- **Quartz Cutter:** Rename the transceiver. The slave’s Jade UI will display the connected master’s name for easier identification and management.

### Ownership & Security (v1.4.4+)

Starting from version 1.4.4, the Wireless Transceiver supports ownership binding for enhanced security and channel isolation:
- **Default Ownership:** Transceivers placed before 1.4.4 are considered **public devices**.
- **Automatic Binding:** From 1.4.4 onward, newly placed transceivers automatically bind to the player who placed them.
- **FTB Teams Support:** Devices can be bound to FTB teams. **Transceivers from different teams or players cannot communicate even if frequencies match**, ensuring channel isolation.

> **Important Update Notice:**  
> When updating from an older version to 1.4.4 or later:
> - If the old **public master** is removed, you must break and replace the corresponding **slave devices** to complete ownership binding, otherwise they will fail to connect.