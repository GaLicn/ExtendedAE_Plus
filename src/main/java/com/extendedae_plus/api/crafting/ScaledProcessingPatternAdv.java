package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Advanced AE 扩展版，额外实现 AdvPatternDetails 接口。
 * 仅在 Advanced AE 加载时使用。
 */
public final class ScaledProcessingPatternAdv extends ScaledProcessingPattern implements IPatternDetails, IAdvPatternDetails {
    private final LinkedHashMap<AEKey, Direction> dirMap;

    public ScaledProcessingPatternAdv(@NotNull IPatternDetails original, long multiplier) {
        super(original, multiplier);
        this.dirMap = ((AdvProcessingPattern) original).getDirectionMap();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink sink) {
        // 如果 sparseInputs 与 inputs 一一对应，则无需 reorder
        if (((AdvProcessingPattern) this.original).getSparseInputs().size() == this.original.getInputs().length) {
            // AEProcessingPattern 的默认逻辑
            this.original.pushInputsToExternalInventory(inputHolder, sink);
            return;
        }

        // 否则必须按 sparse 输入顺序推送
        var allInputs = new KeyCounter();
        for (var counter : inputHolder) allInputs.addAll(counter);

        for (var sparseInput : this.getSparseInputs()) {
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

    @Override
    protected List<GenericStack> getSparseInputs() {
        var original = ((AdvProcessingPattern) this.original).getSparseInputs();
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

    @Override
    public List<GenericStack> getSparseOutputs() {
        var original = ((AdvProcessingPattern) this.original).getSparseOutputs();
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

    @Override
    public boolean directionalInputsSet() {
        return this.dirMap != null && !this.dirMap.isEmpty();
    }

    @Override
    public LinkedHashMap<AEKey, Direction> getDirectionMap() {
        return this.dirMap;
    }

    @Override
    public Direction getDirectionSideForInputKey(AEKey key) {
        return this.dirMap.get(key);
    }
}
