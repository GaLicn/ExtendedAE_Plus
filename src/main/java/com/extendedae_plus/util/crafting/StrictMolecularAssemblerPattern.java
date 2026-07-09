package com.extendedae_plus.util.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 让装配矩阵中的分子装配样板始终只接受编码时的首选输入。
 */
public final class StrictMolecularAssemblerPattern implements IMolecularAssemblerSupportedPattern, ISmartDoublingAwarePattern {

    private static final int DEFAULT_SUPER_MATRIX_BATCH_SIZE = 1024;

    private final IMolecularAssemblerSupportedPattern delegate;
    private final IInput[] strictInputs;
    private final Map<Integer, AEItemKey> strictSlotInputs;
    private boolean allowScaling = true;
    private int multiplierLimit = DEFAULT_SUPER_MATRIX_BATCH_SIZE;

    private StrictMolecularAssemblerPattern(IMolecularAssemblerSupportedPattern delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.strictInputs = this.buildStrictInputs(delegate.getInputs());
        this.strictSlotInputs = this.probeStrictSlotInputs();
    }

    public static IMolecularAssemblerSupportedPattern wrap(IMolecularAssemblerSupportedPattern pattern) {
        return pattern instanceof StrictMolecularAssemblerPattern ? pattern : new StrictMolecularAssemblerPattern(pattern);
    }

    @Override
    public AEItemKey getDefinition() {
        return this.delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return this.strictInputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return this.delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return this.delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        this.delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public ItemStack assemble(Container container, Level level) {
        return this.delegate.assemble(container, level);
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey key, Level level) {
        var expected = this.strictSlotInputs.get(slot);
        return expected != null && expected.equals(key);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return this.delegate.isSlotEnabled(slot);
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
        this.delegate.fillCraftingGrid(table, gridAccessor);
    }

    @Override
    public boolean eap$allowScaling() {
        return this.allowScaling;
    }

    @Override
    public void eap$setAllowScaling(boolean allow) {
        this.allowScaling = allow;
    }

    @Override
    public int eap$getMultiplierLimit() {
        return this.multiplierLimit;
    }

    @Override
    public void eap$setMultiplierLimit(int limit) {
        this.multiplierLimit = limit <= 0 ? DEFAULT_SUPER_MATRIX_BATCH_SIZE : limit;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        return this.delegate.getRemainingItems(container);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StrictMolecularAssemblerPattern other) {
            return this.delegate.equals(other.delegate);
        }
        if (obj instanceof IPatternDetails otherPattern) {
            return this.getDefinition().equals(otherPattern.getDefinition());
        }
        return false;
    }

    @Override
    public String toString() {
        return "Strict[" + this.delegate + "]";
    }

    private IInput[] buildStrictInputs(IInput[] originalInputs) {
        var wrapped = new IInput[originalInputs.length];
        for (int i = 0; i < originalInputs.length; i++) {
            wrapped[i] = new StrictInput(originalInputs[i]);
        }
        return wrapped;
    }

    private Map<Integer, AEItemKey> probeStrictSlotInputs() {
        var slots = new HashMap<Integer, AEItemKey>();
        var probe = new KeyCounter[this.strictInputs.length];
        for (int i = 0; i < this.strictInputs.length; i++) {
            probe[i] = new KeyCounter();
            var possibleInputs = this.strictInputs[i].getPossibleInputs();
            if (possibleInputs.length == 0 || !(possibleInputs[0].what() instanceof AEItemKey itemKey)) {
                continue;
            }
            probe[i].add(itemKey, this.strictInputs[i].getMultiplier());
        }

        // 用首选输入探测实际落格位置，后续 slot 校验只接受这些精确物品。
        this.delegate.fillCraftingGrid(probe, (slot, stack) -> {
            var stackKey = AEItemKey.of(stack);
            if (stackKey != null) {
                slots.put(slot, stackKey);
            }
        });
        return slots;
    }

    private static final class StrictInput implements IInput {
        private final IInput delegate;
        private final GenericStack[] primaryOnly;

        private StrictInput(IInput delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            var possibleInputs = delegate.getPossibleInputs();
            if (possibleInputs.length == 0) {
                this.primaryOnly = possibleInputs;
            } else {
                this.primaryOnly = new GenericStack[] { possibleInputs[0] };
            }
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.primaryOnly;
        }

        @Override
        public long getMultiplier() {
            return this.delegate.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.primaryOnly.length > 0
                    && input != null
                    && this.primaryOnly[0].what().equals(input);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return this.delegate.getRemainingKey(template);
        }
    }
}
