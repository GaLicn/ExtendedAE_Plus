package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 缩放后的处理样板，结构完全模拟 AEProcessingPattern。
 * 保持 sparse/condensed/inputs 的一致性，同时保存原始样板。
 */
public class ScaledProcessingPattern implements IPatternDetails {
    protected final @NotNull IPatternDetails original;
    protected final long multiplier;

    public ScaledProcessingPattern(@NotNull IPatternDetails original, long multiplier) {
        if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
        this.original = original;
        this.multiplier = multiplier;
    }

    public @NotNull IPatternDetails getOriginal() {return this.original;}

    @Override
    public AEItemKey getDefinition() {
        return this.original.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        IInput[] original = this.original.getInputs();
        IInput[] scaled = new IInput[original.length];
        for (int i = 0; i < original.length; i++) {
            scaled[i] = new ScaledInput(original[i], this.multiplier);
        }
        return scaled;
    }

    @Override
    public List<GenericStack> getOutputs() {
        var original = this.original.getOutputs();
        List<GenericStack> scaled = new ArrayList<>(original.size());
        for (GenericStack g : original) {
            if (g != null) {
                scaled.add(new GenericStack(g.what(), g.amount() * this.multiplier));
            }
        }
        return scaled;
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink sink) {
        // 如果 sparseInputs 与 inputs 一一对应，则无需 reorder
        if (((AEProcessingPattern) this.original).getSparseInputs().size() == this.original.getInputs().length) {
            // AEProcessingPattern 的默认逻辑
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, sink);
            return;
        }

        // 否则必须按 sparse 输入顺序推送
        var allInputs = new KeyCounter();
        for (var ctr : inputHolder) {
            allInputs.addAll(ctr);
        }

        var sparse = this.getSparseInputs(); // 使用已缩放倍率的顺序表

        for (var sparseInput : sparse) {
            if (sparseInput == null) continue;

            var key = sparseInput.what();
            long amount = sparseInput.amount();

            long available = allInputs.get(key);
            if (available < amount) {
                throw new IllegalStateException(
                        "Expected " + amount + " of " + key + " but only " + available + " available"
                );
            }

            sink.pushInput(key, amount);
            allInputs.remove(key, amount);
        }
    }

    protected List<GenericStack> getSparseInputs() {
        var original = ((AEProcessingPattern) this.original).getSparseInputs();
        List<GenericStack> scaled = new ArrayList<>(original.size());
        for (GenericStack g : original) {
            if (g != null) {
                scaled.add(new GenericStack(g.what(), g.amount() * this.multiplier));
            } else {
                scaled.add(null); // 保持 null 位
            }
        }
        return scaled;
    }

    public List<GenericStack> getSparseOutputs() {
        var original = ((AEProcessingPattern) this.original).getSparseOutputs();
        List<GenericStack> scaled = new ArrayList<>(original.size());
        for (GenericStack g : original) {
            if (g != null) {
                scaled.add(new GenericStack(g.what(), g.amount() * this.multiplier));
            } else {
                scaled.add(null);
            }
        }
        return scaled;
    }

    // equals / hashCode 必须包含 multiplier！不同倍率 = 不同 key
    @Override
    public int hashCode() {
        int h = this.original.hashCode();
        h = 31 * h + Long.hashCode(this.multiplier);
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
        StringBuilder sb = new StringBuilder("Scaled[Mult=")
                .append(this.multiplier)
                .append("] ");

        sb.append("Inputs: [");
        sb.append(String.join(", ",
                java.util.Arrays.stream(this.original.getInputs())
                        .filter(i -> i.getPossibleInputs() != null && i.getPossibleInputs().length > 0)
                        .map(i -> {
                            GenericStack stack = i.getPossibleInputs()[0];
                            return stack.what() + "×" + i.getMultiplier();
                        })
                        .toArray(String[]::new)
        ));
        sb.append("] ");

        sb.append("Outputs: [");
        sb.append(String.join(", ",
                this.original.getOutputs().stream()
                        .filter(Objects::nonNull)
                        .map(s -> s.what() + "×" + s.amount())
                        .toArray(String[]::new)
        ));
        sb.append("]");

        return sb.toString();
    }


    private record ScaledInput(IInput original, long multiplier) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {return this.original.getPossibleInputs();}

        @Override
        public long getMultiplier() {return this.original.getMultiplier() * this.multiplier;}

        @Override
        public boolean isValid(AEKey input, Level level) {return this.original.isValid(input, level);}

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {return this.original.getRemainingKey(template);}
    }
}
