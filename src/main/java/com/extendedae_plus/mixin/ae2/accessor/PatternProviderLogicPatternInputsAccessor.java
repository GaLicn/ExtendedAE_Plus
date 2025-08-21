package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicPatternInputsAccessor {
    @Accessor("patternInputs")
    Set<AEKey> ext$patternInputs();
}
