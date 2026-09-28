package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import mezz.jei.api.ingredients.IIngredientType;
import mekanism.api.IMekanismAccess;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import org.jetbrains.annotations.Nullable;

public final class AppliedMekanisticsCompat {
	private AppliedMekanisticsCompat() {
	}

	@Nullable
	public static IIngredientType<?> getChemicalIngredientType() {
		return IMekanismAccess.INSTANCE.jeiHelper().getChemicalStackHelper().getIngredientType();
	}

	@Nullable
	public static AEKey toKey(Object ingredient) {
		return ingredient instanceof mekanism.api.chemical.ChemicalStack chemicalStack
				? MekanismKey.of(chemicalStack) : null;
	}

	public static void addBookmark(Object key) {
		if (key instanceof MekanismKey mekanismKey) {
			JeiRuntimeCompat.addBookmark(mekanismKey.getStack());
		}
	}
}
