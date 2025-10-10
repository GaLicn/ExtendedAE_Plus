package com.extendedae_plus.mixin.ae2;

import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AEProcessingPattern.class, remap = false)
public class AEProcessingPatternMixin implements ISmartDoublingAwarePattern {
    @Unique
    private boolean eap$allowScaling = false; // 默认不允许缩放
    @Unique
    private int eap$multiplierLimit = 0; // 模式级别的倍数上限，0 表示不限制

    @Override
    public boolean eap$allowScaling() {
        return eap$allowScaling;
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
