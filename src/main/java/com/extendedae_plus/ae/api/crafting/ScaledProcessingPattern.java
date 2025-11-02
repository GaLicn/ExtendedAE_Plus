package com.extendedae_plus.ae.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ScaledProcessingPattern implements IPatternDetails {

    private final AEProcessingPattern original;
    private final long multiplier;

    public ScaledProcessingPattern(AEProcessingPattern original, long multiplier) {
        if (original == null) throw new IllegalArgumentException("original cannot be null");
        if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
        this.original = original;
        this.multiplier = multiplier;
    }

    public AEProcessingPattern getOriginal() { return original; }
    public long getMultiplier() { return multiplier; }

    @Override
    public AEItemKey getDefinition() {
        return original.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        IPatternDetails.IInput[] orig = original.getInputs();
        IInput[] scaled = new IInput[orig.length];
        for (int i = 0; i < orig.length; i++) {
            scaled[i] = new ScaledInput(orig[i], multiplier);
        }
        return scaled;
    }

    @Override
    public GenericStack[] getOutputs() {
        GenericStack[] orig = original.getOutputs();
        GenericStack[] scaled = new GenericStack[orig.length];
        for (int i = 0; i < orig.length; i++) {
            if (orig[i] != null) {
                scaled[i] = new GenericStack(orig[i].what(), orig[i].amount() * multiplier);
            }
        }
        return scaled;
    }

    // 兼容性方法
    public GenericStack[] getSparseInputs() {
        GenericStack[] orig = original.getSparseInputs();
        GenericStack[] scaled = new GenericStack[orig.length];
        for (int i = 0; i < orig.length; i++) {
            if (orig[i] != null) {
                scaled[i] = new GenericStack(orig[i].what(), orig[i].amount() * multiplier);
            }
        }
        return scaled;
    }

    public GenericStack[] getSparseOutputs() {
        GenericStack[] orig = original.getSparseOutputs();
        GenericStack[] scaled = new GenericStack[orig.length];
        for (int i = 0; i < orig.length; i++) {
            if (orig[i] != null) {
                scaled[i] = new GenericStack(orig[i].what(), orig[i].amount() * multiplier);
            }
        }
        return scaled;
    }

    // equals / hashCode 必须包含 multiplier！不同倍率 = 不同 key
    @Override
    public int hashCode() {
        int h = original.hashCode();
        h = 31 * h + Long.hashCode(multiplier);
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScaledProcessingPattern sp)) return false;
        return sp.original.equals(this.original) && sp.multiplier == this.multiplier;
    }

    @Override
    public String toString() {
        return "Scaled[" + original.getDefinition().getItem() + " × " + multiplier + "]";
    }

    private static class ScaledInput implements IInput {
        private final IInput delegate;
        private final long mul;

        private ScaledInput(IInput delegate, long mul) {
            this.delegate = delegate;
            this.mul = mul;
        }

        @Override public GenericStack[] getPossibleInputs() { return delegate.getPossibleInputs(); }
        @Override public long getMultiplier() { return delegate.getMultiplier() * mul; }
        @Override public boolean isValid(AEKey input, Level level) { return delegate.isValid(input, level); }
        @Override public @Nullable AEKey getRemainingKey(AEKey template) { return delegate.getRemainingKey(template); }
    }
}