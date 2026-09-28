package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Accessor("encodedPatternSlot")
    RestrictedInputSlot eap$getEncodedPatternSlot();

    @Accessor("blankPatternSlot")
    RestrictedInputSlot eap$getBlankPatternSlot();

    @Accessor("encodedInputsInv")
    ConfigInventory eap$getEncodedInputsInv();

    @Accessor("encodedOutputsInv")
    ConfigInventory eap$getEncodedOutputsInv();
}
