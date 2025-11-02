package com.extendedae_plus.ae.api.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails;

import java.util.HashMap;

/**
 * Advanced AE 扩展版，额外实现 AdvPatternDetails 接口。
 * 仅在 Advanced AE 加载时使用。
 */
public final class ScaledProcessingPatternAdv extends ScaledProcessingPattern implements AdvPatternDetails {

    private final AdvPatternDetails adv;

    public ScaledProcessingPatternAdv(AEProcessingPattern original, long multiplier) {
        super(original, multiplier);
        this.adv = (AdvPatternDetails) original;
    }

    @Override
    public boolean directionalInputsSet() {
        return adv.directionalInputsSet();
    }

    @Override
    public HashMap<AEKey, Direction> getDirectionMap() {
        return adv.getDirectionMap();
    }

    @Override
    public Direction getDirectionSideForInputKey(AEKey key) {
        return adv.getDirectionSideForInputKey(key);
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
//        // 使用 lazy 计算的 sparseInputs 与 inputs 来驱动；当两者长度一致时直接委托
//        GenericStack[] sInputs = getSparseInputs();
//        IInput[] ins = getInputs();
//        if (sInputs.length == ins.length) {
//            super.pushInputsToExternalInventory(inputHolder, inputSink);
//            return;
//        }
//
//        KeyCounter allInputs = new KeyCounter();
//        for (KeyCounter counter : inputHolder) {
//            allInputs.addAll(counter);
//        }
//        for (GenericStack sparseInput : sInputs) {
//            if (sparseInput != null) {
//                AEKey key = sparseInput.what();
//                long amount = sparseInput.amount();
//                long available = allInputs.get(key);
//                if (available < amount) {
//                    throw new RuntimeException("Expected at least %d of %s when pushing scaled pattern, but only %d available"
//                            .formatted(amount, key, available));
//                }
//                inputSink.pushInput(key, amount);
//                allInputs.remove(key, amount);
//            }
//        }
    }
}
