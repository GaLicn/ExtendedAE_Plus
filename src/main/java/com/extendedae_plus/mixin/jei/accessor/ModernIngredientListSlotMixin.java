package com.extendedae_plus.mixin.jei.accessor;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.elements.IElement;
import com.extendedae_plus.compat.jei.IngredientListSlotAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListSlot", remap = false)
public interface ModernIngredientListSlotMixin extends IngredientListSlotAccessor {
    @Override
    @Invoker("isBlocked")
    boolean eap$isBlocked();

    @Override
    @Invoker("getOptionalElement")
    Optional<IElement<?>> eap$getOptionalElement();

    @Override
    @Invoker("getArea")
    ImmutableRect2i eap$getArea();

    @Override
    @Invoker("getPadding")
    int eap$getPadding();
}
