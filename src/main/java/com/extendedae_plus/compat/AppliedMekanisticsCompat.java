package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * Runtime-only bridge for Applied Mekanistics.
 *
 * <p>All Applied Mekanistics references live in this compat boundary. The
 * feature remains optional at runtime because callers first check the loaded
 * mod list and this bridge handles unavailable JEI setup gracefully.</p>
 */
public final class AppliedMekanisticsCompat {

    private AppliedMekanisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("appmek") && ModList.get().isLoaded("mekanism");
    }

    public static boolean isChemicalType(@Nullable IIngredientType<?> type) {
        if (!isLoaded() || type == null) {
            return false;
        }

        try {
            return type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                    .getGasStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getInfusionStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getPigmentStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getSlurryStackHelper().getIngredientType().getIngredientClass();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static AEKey toKey(@Nullable Object ingredient) {
        if (!(ingredient instanceof ChemicalStack<?> chemicalStack) || !isLoaded()) {
            return null;
        }

        try {
            return MekanismKey.of(chemicalStack);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
