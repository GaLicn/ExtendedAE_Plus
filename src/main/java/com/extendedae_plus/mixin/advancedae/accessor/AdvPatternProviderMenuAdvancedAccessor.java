package com.extendedae_plus.mixin.advancedae.accessor;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvPatternProviderMenu.class)
public interface AdvPatternProviderMenuAdvancedAccessor {
    @Accessor("logic")
    AdvPatternProviderLogic eap$logic();
}
