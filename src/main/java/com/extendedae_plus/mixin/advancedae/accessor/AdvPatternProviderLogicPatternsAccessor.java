package com.extendedae_plus.mixin.advancedae.accessor;

import appeng.api.crafting.IPatternDetails;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public interface AdvPatternProviderLogicPatternsAccessor {
    @Accessor("patterns")
    List<IPatternDetails> eap$patterns();
}
