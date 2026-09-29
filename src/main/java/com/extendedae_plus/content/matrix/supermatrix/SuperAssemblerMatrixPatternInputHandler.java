package com.extendedae_plus.content.matrix.supermatrix;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;

/** 以单个虚拟插入口暴露超级矩阵样板库存，避免外部存储逐槽扫描。 */
final class SuperAssemblerMatrixPatternInputHandler extends SnapshotJournal<SuperAssemblerMatrixPatternInputHandler.HandlerState>
        implements ResourceHandler<ItemResource> {

    private final InternalInventory[] inventories;
    private final int totalSlots;
    private int inventoryCursor;
    private int slotCursor;
    private boolean knownFull;

    SuperAssemblerMatrixPatternInputHandler(List<SuperAssemblerMatrixCluster.PatternInventorySource> sources) {
        this.inventories = sources.stream()
                .map(SuperAssemblerMatrixCluster.PatternInventorySource::inventory)
                .filter(inventory -> inventory.size() > 0)
                .toArray(InternalInventory[]::new);

        int slots = 0;
        for (var inventory : this.inventories) {
            slots += inventory.size();
        }
        this.totalSlots = slots;
    }

    @Override
    public int size() {
        return this.totalSlots == 0 ? 0 : 1;
    }

    @Override
    public ItemResource getResource(int index) {
        this.checkSlot(index);
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        this.checkSlot(index);
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        this.checkSlot(index);
        return resource.isEmpty() || this.isValid(index, resource) ? 1 : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        this.checkSlot(index);
        return !resource.isEmpty() && !this.knownFull && this.isAcceptedPattern(resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        this.checkSlot(index);
        if (resource.isEmpty() || amount <= 0 || this.knownFull || !this.isAcceptedPattern(resource.toStack(1))) {
            return 0;
        }

        int inserted = 0;
        int checkedSlots = 0;
        int currentInventory = this.inventoryCursor;
        int currentSlot = this.slotCursor;
        while (inserted < amount && checkedSlots < this.totalSlots) {
            var inventory = this.inventories[currentInventory];
            if (inventory.getStackInSlot(currentSlot).isEmpty()) {
                if (inserted == 0) {
                    this.updateSnapshots(transaction);
                }
                inventory.setItemDirect(currentSlot, resource.toStack(1));
                inserted++;
            }
            checkedSlots++;

            currentSlot++;
            if (currentSlot >= inventory.size()) {
                currentInventory = (currentInventory + 1) % this.inventories.length;
                currentSlot = 0;
            }
        }

        if (inserted > 0) {
            this.inventoryCursor = currentInventory;
            this.slotCursor = currentSlot;
        }
        if (checkedSlots == this.totalSlots && inserted < amount) {
            this.updateSnapshots(transaction);
            this.knownFull = true;
        }
        return inserted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        this.checkSlot(index);
        return 0;
    }

    void invalidateFullState() {
        this.knownFull = false;
    }

    private boolean isAcceptedPattern(ItemStack stack) {
        for (var inventory : this.inventories) {
            if (inventory.size() > 0) {
                return inventory.isItemValid(0, stack);
            }
        }
        return false;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.size()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range");
        }
    }

    @Override
    protected HandlerState createSnapshot() {
        var stacks = new ArrayList<List<ItemStack>>(this.inventories.length);
        for (var inventory : this.inventories) {
            var contents = new ArrayList<ItemStack>(inventory.size());
            for (int slot = 0; slot < inventory.size(); slot++) {
                contents.add(inventory.getStackInSlot(slot).copy());
            }
            stacks.add(contents);
        }
        return new HandlerState(stacks, this.inventoryCursor, this.slotCursor, this.knownFull);
    }

    @Override
    protected void revertToSnapshot(HandlerState state) {
        for (int inventoryIndex = 0; inventoryIndex < this.inventories.length; inventoryIndex++) {
            var inventory = this.inventories[inventoryIndex];
            var contents = state.contents().get(inventoryIndex);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setItemDirect(slot, contents.get(slot).copy());
            }
        }
        this.inventoryCursor = state.inventoryCursor();
        this.slotCursor = state.slotCursor();
        this.knownFull = state.knownFull();
    }

    record HandlerState(List<List<ItemStack>> contents, int inventoryCursor, int slotCursor, boolean knownFull) {
    }
}
