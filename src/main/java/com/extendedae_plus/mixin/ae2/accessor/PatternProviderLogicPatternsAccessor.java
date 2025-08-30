package com.extendedae_plus.mixin.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicPatternsAccessor {
    @Accessor("patterns")
    List<IPatternDetails> eap$patterns();
}
