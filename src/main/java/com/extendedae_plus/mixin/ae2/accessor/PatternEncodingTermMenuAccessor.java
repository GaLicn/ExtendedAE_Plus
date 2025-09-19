package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Accessor(value = "encodedPatternSlot",remap = false)
    RestrictedInputSlot eap$getEncodedPatternSlot();

    @Accessor(value = "blankPatternSlot",remap = false)
    RestrictedInputSlot eap$getBlankPatternSlot();
}
