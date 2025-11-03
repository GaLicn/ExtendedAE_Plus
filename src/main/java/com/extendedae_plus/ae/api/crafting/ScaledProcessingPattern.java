package com.extendedae_plus.ae.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScaledProcessingPattern implements IPatternDetails {
    private final AEProcessingPattern original;
    private final long multiplier;

    public ScaledProcessingPattern(@NotNull AEProcessingPattern original, long multiplier) {
        if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
        this.original = original;
        this.multiplier = multiplier;
    }

    public AEProcessingPattern getOriginal() {return original;}

    public long getMultiplier() {return multiplier;}

    @Override
    public final AEItemKey getDefinition() {
        return original.getDefinition();
    }

    @Override
    public final IInput[] getInputs() {
        IPatternDetails.IInput[] original = this.original.getInputs();
        IInput[] scaled = new IInput[original.length];
        for (int i = 0; i < original.length; i++) {
            scaled[i] = new ScaledInput(original[i], multiplier);
        }
        return scaled;
    }

    @Override
    public final GenericStack[] getOutputs() {
        GenericStack[] original = this.original.getOutputs();
        GenericStack[] scaled = new GenericStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                scaled[i] = new GenericStack(original[i].what(), original[i].amount() * multiplier);
            }
        }
        return scaled;
    }

    // 兼容性方法
    public final GenericStack[] getSparseInputs() {
        GenericStack[] original = this.original.getSparseInputs();
        GenericStack[] scaled = new GenericStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                scaled[i] = new GenericStack(original[i].what(), original[i].amount() * multiplier);
            }
        }
        return scaled;
    }

    public final GenericStack[] getSparseOutputs() {
        GenericStack[] original = this.original.getSparseOutputs();
        GenericStack[] scaled = new GenericStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                scaled[i] = new GenericStack(original[i].what(), original[i].amount() * multiplier);
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
        StringBuilder sb = new StringBuilder();
        sb.append("Scaled[Mult=").append(multiplier).append("] ");

        // 输入
        sb.append("Inputs: [");
        IInput[] inputs = original.getInputs();
        for (int i = 0; i < inputs.length; i++) {
            IInput input = inputs[i];
            GenericStack[] stacks = input.getPossibleInputs();
            if (stacks != null && stacks.length > 0) {
                GenericStack stack = stacks[0];
                sb.append(stack.what()).append("×").append(input.getMultiplier());
                if (i < inputs.length - 1) sb.append(", ");
            }
        }
        sb.append("] ");

        // 输出
        sb.append("Outputs: [");
        GenericStack[] outputs = original.getOutputs();
        for (int i = 0; i < outputs.length; i++) {
            GenericStack stack = outputs[i];
            if (stack != null) {
                sb.append(stack.what()).append("×").append(stack.amount());
                if (i < outputs.length - 1) sb.append(", ");
            }
        }
        sb.append("]");

        return sb.toString();
    }

    private record ScaledInput(IInput delegate, long mul) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {return delegate.getPossibleInputs();}

        @Override
        public long getMultiplier() {return delegate.getMultiplier() * mul;}

        @Override
        public boolean isValid(AEKey input, Level level) {return delegate.isValid(input, level);}

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {return delegate.getRemainingKey(template);}
    }
}