package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;

import java.util.LinkedHashMap;

/**
 * Advanced AE 扩展版，额外实现 AdvPatternDetails 接口。
 * 仅在 Advanced AE 加载时使用。
 */
public final class ScaledProcessingPatternAdv extends ScaledProcessingPattern implements AdvPatternDetails {
    private final LinkedHashMap<AEKey, Direction> dirMap;

    public ScaledProcessingPatternAdv(AEProcessingPattern original, long multiplier) {
        super(original, multiplier);
        this.dirMap = ((AdvProcessingPattern) original).getDirectionMap();
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

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, IPatternDetails.PatternInputSink inputSink) {
        super.pushInputsToExternalInventory(inputHolder, inputSink);
    }
}
