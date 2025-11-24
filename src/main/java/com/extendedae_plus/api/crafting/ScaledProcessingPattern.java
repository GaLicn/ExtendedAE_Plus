package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 缩放后的处理样板，结构完全模拟 AEProcessingPattern。
 * 保持 sparse/condensed/inputs 的一致性，同时保存原始样板。
 */
public final class ScaledProcessingPattern implements IPatternDetails {

    private final AEProcessingPattern original;        // 原始样板引用
    private final AEItemKey definition;                // 样板物品
    private final List<GenericStack> sparseInputs;     // 缩放后的稀疏输入（List 以适配 1.21 API）
    private final List<GenericStack> sparseOutputs;    // 缩放后的稀疏输出（List 以适配 1.21 API）
    private final IInput[] inputs;                     // 缩放后的压缩输入
    private final List<GenericStack> condensedOutputs; // 缩放后的压缩输出（List 以适配 1.21 API）

    public ScaledProcessingPattern(
            AEProcessingPattern original,
            AEItemKey definition,
            List<GenericStack> sparseInputs,
            List<GenericStack> sparseOutputs,
            IInput[] inputs,
            List<GenericStack> condensedOutputs
    ) {
        this.original = Objects.requireNonNull(original);
        this.definition = Objects.requireNonNull(definition);
        this.sparseInputs = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(sparseInputs)));
        this.sparseOutputs = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(sparseOutputs)));
        this.inputs = Objects.requireNonNull(inputs);
        this.condensedOutputs = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(condensedOutputs)));
    }

    /* -------------------- API 实现 -------------------- */

    public AEProcessingPattern getOriginal() {
        return this.original;
    }

    @Override
    public AEItemKey getDefinition() {
        return this.definition;
    }

    @Override
    public IInput[] getInputs() {
        return this.inputs;
    }

    @Override
    public GenericStack getPrimaryOutput() {
        if (!this.condensedOutputs.isEmpty()) return this.condensedOutputs.get(0);
        return this.original.getPrimaryOutput();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return this.condensedOutputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return this.original.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        // 保持和 AEProcessingPattern 一致，用 sparseInputs 驱动
        if (this.sparseInputs.size() == this.inputs.length) {
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
        } else {
            KeyCounter allInputs = new KeyCounter();
            for (KeyCounter counter : inputHolder) {
                allInputs.addAll(counter);
            }
            for (GenericStack sparseInput : this.sparseInputs) {
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

    public List<GenericStack> getSparseInputs() {
        return this.sparseInputs;
    }

    public List<GenericStack> getSparseOutputs() {
        return this.sparseOutputs;
    }

    /* -------------------- 缩放输入代理 -------------------- */

    public static final class Input implements IPatternDetails.IInput {
        private final GenericStack[] template;
        private final long multiplier;

        public Input(GenericStack[] template, long multiplier) {
            this.template = template;
            this.multiplier = multiplier;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.template;
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.matches(this.template[0]);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
