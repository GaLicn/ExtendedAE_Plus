package com.extendedae_plus.compat;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * 保持库存对象身份稳定，同时动态限制对外可访问的槽位范围。
 */
public final class DynamicSizeInternalInventory implements InternalInventory {
    private final InternalInventory delegate;
    private final IntSupplier sizeSupplier;

    public DynamicSizeInternalInventory(InternalInventory delegate, IntSupplier sizeSupplier) {
        this.delegate = Objects.requireNonNull(delegate);
        this.sizeSupplier = Objects.requireNonNull(sizeSupplier);
    }

    /** 菜单固定创建全部槽位时使用，其他外部调用仍通过动态视图访问。 */
    public InternalInventory getBackingInventory() {
        return this.delegate;
    }

    @Override
    public int size() {
        return Math.max(0, Math.min(this.delegate.size(), this.sizeSupplier.getAsInt()));
    }

    @Override
    public int getSlotLimit(int slot) {
        this.checkSlot(slot);
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        this.checkSlot(slotIndex);
        return this.delegate.getStackInSlot(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        this.checkSlot(slotIndex);
        this.delegate.setItemDirect(slotIndex, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        this.checkSlot(slot);
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        this.checkSlot(slot);
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public void sendChangeNotification(int slot) {
        this.checkSlot(slot);
        this.delegate.sendChangeNotification(slot);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.size()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " outside exposed inventory size " + this.size());
        }
    }
}
