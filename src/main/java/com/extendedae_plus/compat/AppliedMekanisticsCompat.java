package com.extendedae_plus.compat;

import org.jetbrains.annotations.Nullable;

import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;

import appeng.api.stacks.AEKey;

public final class AppliedMekanisticsCompat {
	private AppliedMekanisticsCompat() {
	}

	/**
	 * Returns the JEI ingredient type registered by Mekanism, when Applied
	 * Mekanistics and Mekanism are available at runtime.
	 */
	@Nullable
	public static IIngredientType<?> getChemicalIngredientType() {
		try {
			return IMekanismAccess.INSTANCE.jeiHelper().getChemicalStackHelper().getIngredientType();
		} catch (Throwable ignored) {
			return null;
		}
	}

	@Nullable
	public static AEKey toKey(Object ingredient) {
		if (!(ingredient instanceof ChemicalStack chemicalStack)) {
			return null;
		}
		return MekanismKey.of(chemicalStack);
	}

	public static void addBookmark(Object key) {
		if (key instanceof MekanismKey mekanismKey) {
			JeiRuntimeCompat.addBookmark(mekanismKey.getStack());
		}
	}
}
