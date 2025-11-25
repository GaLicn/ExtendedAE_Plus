package com.extendedae_plus.mixin.advancedae;

import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AdvProcessingPattern.class, remap = false)
public class AdvProcessingPatternMixin implements ISmartDoublingAwarePattern {
    @Unique
    private boolean eap$allowScaling = false; // 默认不允许缩放
    @Unique
    private int eap$multiplierLimit = 0; // 模式级别的倍数上限，0 表示不限制

    @Override
    public boolean eap$allowScaling() {
        return this.eap$allowScaling;
    }

    @Override
    public void eap$setAllowScaling(boolean allow) {
        this.eap$allowScaling = allow;
    }

    @Override
    public int eap$getMultiplierLimit() {
        return this.eap$multiplierLimit;
    }

    @Override
    public void eap$setMultiplierLimit(int limit) {
        this.eap$multiplierLimit = Math.max(0, limit);
    }
}