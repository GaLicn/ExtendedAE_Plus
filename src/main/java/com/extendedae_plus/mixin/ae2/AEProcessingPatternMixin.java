package com.extendedae_plus.mixin.ae2;

import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AEProcessingPattern.class, remap = false)
public class AEProcessingPatternMixin implements ISmartDoublingAwarePattern {
    @Unique
    private boolean eap$allowScaling = false; // 默认不允许缩放

    @Override
    public boolean eap$allowScaling() {
        return this.eap$allowScaling;
    }

    @Override
    public void eap$setAllowScaling(boolean allow) {
        this.eap$allowScaling = allow;
    }
}
