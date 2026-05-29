package com.extendedae_plus.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.extendedae_plus.api.crafting.IForcedCraftingPlan;

import java.util.Map;

public class ForcedCraftingPlan implements ICraftingPlan, IForcedCraftingPlan {
    private final ICraftingPlan delegate;
    private final KeyCounter manualMissingItems;

    public ForcedCraftingPlan(ICraftingPlan delegate) {
        this.delegate = delegate;
        this.manualMissingItems = copy(delegate.missingItems());
    }

    @Override
    public KeyCounter eap$getManualMissingItems() {
        return copy(this.manualMissingItems);
    }

    @Override
    public GenericStack finalOutput() {
        return this.delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return this.delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return false;
    }

    @Override
    public boolean multiplePaths() {
        return this.delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return this.delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return this.delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return new KeyCounter();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return this.delegate.patternTimes();
    }

    private static KeyCounter copy(KeyCounter source) {
        var copy = new KeyCounter();
        for (var entry : source) {
            copy.add(entry.getKey(), entry.getLongValue());
        }
        return copy;
    }
}
