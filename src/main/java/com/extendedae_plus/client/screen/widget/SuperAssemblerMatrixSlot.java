package com.extendedae_plus.client.screen.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.glodblock.github.extendedae.util.MutableSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SuperAssemblerMatrixSlot extends AppEngSlot {

    private final AppEngInternalInventory inventory;
    private final long id;
    private final int offset;

    public SuperAssemblerMatrixSlot(AppEngInternalInventory machineInv, int machineInvSlot, int offset, long id,
            int x, int y) {
        super(machineInv, machineInvSlot);
        this.inventory = machineInv;
        this.id = id;
        this.offset = offset;
        ((MutableSlot) this).setXPos(x);
        ((MutableSlot) this).setYPos(y);
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
    public ItemStack getDisplayStack() {
        var stack = super.getDisplayStack();
        if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem patternItem) {
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
