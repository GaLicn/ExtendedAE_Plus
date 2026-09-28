package com.extendedae_plus.compat.jei;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.elements.IElement;

import java.util.Optional;

public interface IngredientListSlotAccessor {
    boolean eap$isBlocked();

    Optional<IElement<?>> eap$getOptionalElement();

    ImmutableRect2i eap$getArea();

    int eap$getPadding();
}
