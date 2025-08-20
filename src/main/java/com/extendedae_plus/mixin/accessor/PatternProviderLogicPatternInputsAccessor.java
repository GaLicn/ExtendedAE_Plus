package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicPatternInputsAccessor {
    @Accessor("patternInputs")
    Set<AEKey> ext$patternInputs();
}
