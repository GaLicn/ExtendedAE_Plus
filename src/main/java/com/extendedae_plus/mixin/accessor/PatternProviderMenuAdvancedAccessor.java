package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.menu.implementations.PatternProviderMenu;
import appeng.helpers.patternprovider.PatternProviderLogic;

@Mixin(PatternProviderMenu.class)
public interface PatternProviderMenuAdvancedAccessor {
    @Accessor("logic")
    PatternProviderLogic ext$logic();
}
