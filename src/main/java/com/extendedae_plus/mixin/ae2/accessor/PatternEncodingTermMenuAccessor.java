package com.extendedae_plus.mixin.ae2.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Accessor("encodedPatternSlot")
    RestrictedInputSlot eap$getEncodedPatternSlot();

    @Accessor("blankPatternSlot")
    RestrictedInputSlot eap$getBlankPatternSlot();

    @Invoker("encodePattern")
    ItemStack eaep$encodePattern();

    @Invoker("isPattern")
    boolean eaep$isPattern(ItemStack output);
}
