package com.extendedae_plus.ae.api.crafting;

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
public class ScaledProcessingPattern implements IPatternDetails {

    // 最小化实例字段：只保留原始样板引用、定义和倍数
    private final AEProcessingPattern original; // 原始样板引用
    private final AEItemKey definition;         // 样板物品（直接委托自 original）
    private final long multiplier;              // 乘数（外部可视为视图参数）

    // 延迟计算缓存（轻量化实例时避免在构造器中分配大数组）
    private transient volatile IInput[] inputsCache;
    private transient volatile GenericStack[] outputsCache;
    private transient volatile GenericStack[] sparseInputsCache;
    private transient volatile GenericStack[] sparseOutputsCache;

    public ScaledProcessingPattern(AEProcessingPattern original, AEItemKey definition, long multiplier) {
        this.original = Objects.requireNonNull(original);
        this.definition = Objects.requireNonNull(definition);
        this.multiplier = multiplier <= 0 ? 1L : multiplier;
    }

    /* -------------------- API 实现 -------------------- */

    public AEProcessingPattern getOriginal() {
        return original;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        // 比较另一个缩放样板，比较其 original 字段
        if (o instanceof ScaledProcessingPattern sp) {
            return this.original.equals(sp.getOriginal());
        }
        // 委托给原始样板的 equals（比如遇到原始的 AEProcessingPattern）
        return this.original.equals(o);
    }

    @Override
    public final int hashCode() {
        return original.hashCode();
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        IInput[] cached = this.inputsCache;
        if (cached == null) {
            synchronized (this) {
                cached = this.inputsCache;
                if (cached == null) {
                    var base = original.getInputs();
                    IInput[] arr = new IInput[base.length];
                    for (int i = 0; i < base.length; i++) {
                        var in = base[i];
                        // 不复制 template 数组，直接复用原始的 possible inputs；仅放大 multiplier
                        arr[i] = new Input(in.getPossibleInputs(), in.getMultiplier() * this.multiplier);
                    }
                    this.inputsCache = arr;
                    cached = arr;
                }
            }
        }
        return cached;
    }

    @Override
    public GenericStack[] getOutputs() {
        GenericStack[] cached = this.outputsCache;
        if (cached == null) {
            synchronized (this) {
                cached = this.outputsCache;
                if (cached == null) {
                    var baseOutputs = original.getOutputs();
                    GenericStack[] arr = new GenericStack[baseOutputs.length];
                    for (int i = 0; i < baseOutputs.length; i++) {
                        var o = baseOutputs[i];
                        if (o != null) arr[i] = new GenericStack(o.what(), o.amount() * this.multiplier);
                    }
                    this.outputsCache = arr;
                    cached = arr;
                }
            }
        }
        return cached;
    }

    public GenericStack[] getSparseInputs() {
        GenericStack[] cached = this.sparseInputsCache;
        if (cached == null) {
            synchronized (this) {
                cached = this.sparseInputsCache;
                if (cached == null) {
                    var base = original.getSparseInputs();
                    GenericStack[] arr = new GenericStack[base.length];
                    for (int i = 0; i < base.length; i++) {
                        var v = base[i];
                        if (v != null) arr[i] = new GenericStack(v.what(), v.amount() * this.multiplier);
                    }
                    this.sparseInputsCache = arr;
                    cached = arr;
                }
            }
        }
        return cached;
    }

    public GenericStack[] getSparseOutputs() {
        GenericStack[] cached = this.sparseOutputsCache;
        if (cached == null) {
            synchronized (this) {
                cached = this.sparseOutputsCache;
                if (cached == null) {
                    var base = original.getSparseOutputs();
                    GenericStack[] arr = new GenericStack[base.length];
                    for (int i = 0; i < base.length; i++) {
                        var v = base[i];
                        if (v != null) arr[i] = new GenericStack(v.what(), v.amount() * this.multiplier);
                    }
                    this.sparseOutputsCache = arr;
                    cached = arr;
                }
            }
        }
        return cached;
    }

    @Override
    public GenericStack getPrimaryOutput() {
        var outs = getOutputs();
        if (outs.length > 0 && outs[0] != null) return outs[0];
        return original.getPrimaryOutput();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return original.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        // 使用 lazy 计算的 sparseInputs 与 inputs 来驱动；当两者长度一致时直接委托
        GenericStack[] sInputs = getSparseInputs();
        IInput[] ins = getInputs();
        if (sInputs.length == ins.length) {
            IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
            return;
        }

        KeyCounter allInputs = new KeyCounter();
        for (KeyCounter counter : inputHolder) {
            allInputs.addAll(counter);
        }
        for (GenericStack sparseInput : sInputs) {
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
