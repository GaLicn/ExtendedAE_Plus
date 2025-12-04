---
navigation:
  parent: introduction/index.md
  title: Channel Card
  position: 2
  icon: extendedae_plus:channel_card
categories:
  - extendedae_plus items
item_ids:
  - extendedae_plus:channel_card
---

# Channel Card

<ItemImage id="extendedae_plus:channel_card" scale="4" />

The **Channel Card** is an upgrade card that encapsulates the slave-end functionality of the <ItemLink id="extendedae_plus:wireless_transceiver" /> into a compact form.  
When installed into compatible AE devices, it allows them to connect directly to a wireless transceiver master of the same frequency — no cables required.

## Function & Usage

### Basic Features
- **Wireless Connection**:  
  AE devices equipped with a Channel Card will automatically search for and connect to a **wireless transceiver master** operating on the **same frequency**, gaining access to its ME network channels.
- **Frequency Adjustment**:
  - **Right-click while holding the card**: **Increase** its frequency.
  - **Shift + Right-click**: **Decrease** its frequency.

### Ownership Binding (v1.4.4+)
- **Binding Operation**:  
  Hold the Channel Card and **Shift + Left-click** (without targeting a block) to bind it to your **team** or **personal UUID**.
- **Binding Requirement**:  
  The card **must be bound** before it can connect to a wireless transceiver master with matching ownership.

### Supported Devices
The Channel Card can be installed into the following AE devices (and their ExtendedAE variants) to grant them wireless connectivity. Once installed, the device will automatically attempt to connect to a wireless transceiver master of the same frequency.

- <ItemLink id="ae2:interface" />
- <ItemLink id="ae2:import_bus" />
- <ItemLink id="ae2:export_bus" />
- <ItemLink id="ae2:storage_bus" />
- <ItemLink id="ae2:pattern_provider" />

## Workflow

1. Ensure a wireless transceiver **master** with the desired frequency and mode is connected to the source ME network.
2. Hold the Channel Card and **Shift + Left-click** to complete ownership binding.
3. Adjust the card’s frequency using **Right-click** or **Shift + Right-click**.
4. Install the Channel Card into the target AE device (e.g., ME Interface).
5. The device will automatically connect to the wireless transceiver master with the same frequency and matching ownership.

> **Note**:  
> Both the Channel Card and the wireless transceiver master must have **matching frequency** and **ownership** (v1.4.4+) to successfully establish a connection.