package com.extendedae_plus.client.screen.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SuperAssemblerMatrixSlot extends Slot {

    private final AppEngInternalInventory inventory;
    private final long id;
    private final int offset;

    public SuperAssemblerMatrixSlot(AppEngInternalInventory machineInv, int machineInvSlot, int offset, long id,
            int x, int y) {
        super(new SimpleContainer(machineInv.size()), machineInvSlot, x, y);
        this.inventory = machineInv;
        this.id = id;
        this.offset = offset;
    }

    public int getActuallySlot() {
        return this.getSlotIndex() + this.offset;
    }

    public long getID() {
        return this.id;
    }

    public ItemStack getStoredStack() {
        return this.inventory.getStackInSlot(this.getSlotIndex());
    }

    @Override
    public ItemStack getItem() {
        var stack = this.getStoredStack();
        if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem<?> patternItem) {
            var output = patternItem.getOutput(stack);
            if (!output.isEmpty()) {
                return output;
            }
        }
        return stack;
    }

    @Override
    public boolean hasItem() {
        return !this.getStoredStack().isEmpty();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public void set(ItemStack stack) {
    }

    public void initialize(ItemStack stack) {
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
