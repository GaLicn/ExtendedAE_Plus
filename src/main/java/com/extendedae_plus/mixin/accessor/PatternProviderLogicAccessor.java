package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Accessor("host")
    PatternProviderLogicHost ext$host();
}
