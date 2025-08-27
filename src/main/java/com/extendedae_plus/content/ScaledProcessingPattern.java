package com.extendedae_plus.content;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 缩放后的处理样板，结构完全模拟 AEProcessingPattern。
 * 保持 sparse/condensed/inputs 的一致性，同时保存原始样板。
 */
public final class ScaledProcessingPattern implements IPatternDetails {

    private final AEProcessingPattern original;        // 原始样板引用
    private final AEItemKey definition;                // 样板物品
    private final GenericStack[] sparseInputs;         // 缩放后的稀疏输入
    private final GenericStack[] sparseOutputs;        // 缩放后的稀疏输出
    private final IInput[] inputs;                     // 缩放后的压缩输入
    private final GenericStack[] condensedOutputs;     // 缩放后的压缩输出

    public ScaledProcessingPattern(
            AEProcessingPattern original,
            AEItemKey definition,
            GenericStack[] sparseInputs,
            GenericStack[] sparseOutputs,
            IInput[] inputs,
            GenericStack[] condensedOutputs
    ) {
        this.original = Objects.requireNonNull(original);
        this.definition = Objects.requireNonNull(definition);
        this.sparseInputs = Objects.requireNonNull(sparseInputs);
        this.sparseOutputs = Objects.requireNonNull(sparseOutputs);
        this.inputs = Objects.requireNonNull(inputs);
        this.condensedOutputs = Objects.requireNonNull(condensedOutputs);
    }

    /* -------------------- API 实现 -------------------- */

    public AEProcessingPattern getOriginal() {
        return original;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return condensedOutputs;
    }

    public GenericStack[] getSparseInputs() {
        return sparseInputs;
    }

    public GenericStack[] getSparseOutputs() {
        return sparseOutputs;
    }

    @Override
    public GenericStack getPrimaryOutput() {
        if (condensedOutputs.length > 0) return condensedOutputs[0];
        return original.getPrimaryOutput();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return original.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        // 保持和 AEProcessingPattern 一致，用 sparseInputs 驱动
        if (sparseInputs.length == inputs.length) {
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
        } else {
            KeyCounter allInputs = new KeyCounter();
            for (KeyCounter counter : inputHolder) {
                allInputs.addAll(counter);
            }
            for (GenericStack sparseInput : sparseInputs) {
                if (sparseInput != null) {
                    AEKey key = sparseInput.what();
                    long amount = sparseInput.amount();
                    long available = allInputs.get(key);
                    if (available < amount) {
                        throw new RuntimeException("Expected at least %d of %s when pushing scaled pattern, but only %d available"
                                .formatted(amount, key, available));
                    }
                    inputSink.pushInput(key, amount);
                    allInputs.remove(key, amount);
                }
            }
        }
    }

    /* -------------------- 缩放输入代理 -------------------- */

    public static final class Input implements IPatternDetails.IInput {
        private final GenericStack[] template;
        private final long multiplier;

        public Input(GenericStack[] template, long multiplier) {
            this.template = template;
            this.multiplier = multiplier;
        }

        public GenericStack[] getPossibleInputs() {
            return this.template;
        }

        public long getMultiplier() {
            return this.multiplier;
        }

        public boolean isValid(AEKey input, Level level) {
            return input.matches(this.template[0]);
        }

        public @Nullable AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
