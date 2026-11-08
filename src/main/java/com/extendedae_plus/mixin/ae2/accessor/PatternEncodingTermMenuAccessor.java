package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Accessor(value = "encodedPatternSlot",remap = false)
    RestrictedInputSlot eap$getEncodedPatternSlot();

    @Accessor(value = "blankPatternSlot",remap = false)
    RestrictedInputSlot eap$getBlankPatternSlot();

    @Accessor(value = "encodedInputsInv", remap = false)
    ConfigInventory eap$getEncodedInputsInv();

    @Accessor(value = "encodedOutputsInv", remap = false)
    ConfigInventory eap$getEncodedOutputsInv();
}
