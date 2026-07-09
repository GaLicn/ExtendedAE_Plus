package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScaledMolecularAssemblerPattern implements IMolecularAssemblerSupportedPattern {
    private final @NotNull IMolecularAssemblerSupportedPattern original;
    private final long multiplier;

    public ScaledMolecularAssemblerPattern(@NotNull IMolecularAssemblerSupportedPattern original, long multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be > 0");
        }
        this.original = Objects.requireNonNull(original, "original");
        this.multiplier = multiplier;
    }

    public @NotNull IMolecularAssemblerSupportedPattern getOriginal() {
        return this.original;
    }

    public long getMultiplier() {
        return this.multiplier;
    }

    @Override
    public AEItemKey getDefinition() {
        return this.original.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        var originalInputs = this.original.getInputs();
        var scaledInputs = new IInput[originalInputs.length];
        for (int i = 0; i < originalInputs.length; i++) {
            scaledInputs[i] = new ScaledInput(originalInputs[i], this.multiplier);
        }
        return scaledInputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        var originalOutputs = this.original.getOutputs();
        var scaledOutputs = new ArrayList<GenericStack>(originalOutputs.size());
        for (var output : originalOutputs) {
            if (output != null) {
                scaledOutputs.add(new GenericStack(output.what(), output.amount() * this.multiplier));
            }
        }
        return scaledOutputs;
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return this.original.getTooltip(level, flags);
    }

    @Override
    public ItemStack assemble(CraftingInput input, Level level) {
        return this.original.assemble(input, level);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return this.original.getRemainingItems(input);
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey key, Level level) {
        return this.original.isItemValid(slot, key, level);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return this.original.isSlotEnabled(slot);
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
        if (this.multiplier == 1) {
            this.original.fillCraftingGrid(table, gridAccessor);
            return;
        }

        var singleCraftTable = divideCounters(table, this.multiplier);
        if (singleCraftTable == null) {
            return;
        }

        // 用单次输入生成装配网格，真实输入在确认成功后整批消费。
        this.original.fillCraftingGrid(singleCraftTable, gridAccessor);
        if (!areCountersEmpty(singleCraftTable)) {
            return;
        }

        for (var counter : table) {
            counter.clear();
        }
    }

    @Override
    public int hashCode() {
        int hash = this.original.hashCode();
        hash = 31 * hash + Long.hashCode(this.multiplier);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ScaledMolecularAssemblerPattern other)) {
            return false;
        }
        return this.multiplier == other.multiplier && this.original.equals(other.original);
    }

    @Override
    public String toString() {
        return "ScaledMolecular[Mult=" + this.multiplier + "] " + this.original;
    }

    private static KeyCounter[] divideCounters(KeyCounter[] table, long divisor) {
        var divided = new KeyCounter[table.length];
        for (int i = 0; i < table.length; i++) {
            divided[i] = new KeyCounter();
            for (var entry : table[i]) {
                long amount = entry.getLongValue();
                if (amount % divisor != 0) {
                    return null;
                }
                divided[i].add(entry.getKey(), amount / divisor);
            }
        }
        return divided;
    }

    private static boolean areCountersEmpty(KeyCounter[] counters) {
        for (var counter : counters) {
            counter.removeZeros();
            if (!counter.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record ScaledInput(IPatternDetails.IInput original, long multiplier) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return this.original.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return this.original.getMultiplier() * this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.original.isValid(input, level);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return this.original.getRemainingKey(template);
        }
    }
}
