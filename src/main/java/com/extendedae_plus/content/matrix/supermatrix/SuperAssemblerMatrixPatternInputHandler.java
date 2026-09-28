package com.extendedae_plus.content.matrix.supermatrix;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** 以单个虚拟插入口暴露超级矩阵样板库存，避免外部存储逐槽扫描。 */
final class SuperAssemblerMatrixPatternInputHandler implements IItemHandler {

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
    public int getSlots() {
        return this.totalSlots == 0 ? 0 : 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        this.checkSlot(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        this.checkSlot(slot);
        if (stack.isEmpty() || this.knownFull || !this.isAcceptedPattern(stack)) {
            return stack;
        }

        var remainder = stack;
        int checkedSlots = 0;
        int currentInventory = this.inventoryCursor;
        int currentSlot = this.slotCursor;

        // 模拟和实际插入都从同一游标开始；实际成功后游标直接指向下一个候选槽。
        while (checkedSlots < this.totalSlots && !remainder.isEmpty()) {
            var inventory = this.inventories[currentInventory];
            if (inventory.getStackInSlot(currentSlot).isEmpty()) {
                if (!simulate) {
                    var insertedPattern = remainder.copy();
                    insertedPattern.setCount(1);
                    // 样板槽固定单件写入，避免容量卡兼容层对每个候选槽重复反射查询。
                    inventory.setItemDirect(currentSlot, insertedPattern);
                }
                remainder = shrinkByOne(remainder);
            }
            checkedSlots++;

            currentSlot++;
            if (currentSlot >= inventory.size()) {
                currentInventory = (currentInventory + 1) % this.inventories.length;
                currentSlot = 0;
            }
        }

        if (!simulate) {
            this.inventoryCursor = currentInventory;
            this.slotCursor = currentSlot;
        }
        if (checkedSlots == this.totalSlots && !remainder.isEmpty()) {
            this.knownFull = true;
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        this.checkSlot(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        this.checkSlot(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        this.checkSlot(slot);
        return !stack.isEmpty() && !this.knownFull && this.isAcceptedPattern(stack);
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

    private static ItemStack shrinkByOne(ItemStack stack) {
        if (stack.getCount() == 1) {
            return ItemStack.EMPTY;
        }
        var remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    private void checkSlot(int slot) {
        if (slot != 0 || this.totalSlots == 0) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
    }
}
