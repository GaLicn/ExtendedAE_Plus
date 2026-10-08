package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

/**
 * Applied Mekanistics 的桥接。
 * <p>
 * 本类直接引用 mekanism / appmek 的类，加载即需要两者在场，
 * 因此只能在 {@link com.extendedae_plus.util.ModCheckUtils#isAppMekLoading()} 为真之后调用。
 */
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
		} catch (Throwable error) {
			ExtendedAEPlus.LOGGER.warn("Failed to resolve Mekanism JEI chemical ingredient type", error);
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

	/**
	 * JEI 化学品条目 → AE2 {@link GenericStack}。
	 * <p>
	 * JEI 悬停给出的是已携带数量的 {@link ChemicalStack}，两侧数量单位同为 mB，
	 * 因此直接采用栈内数量。EMI 侧不经本方法，改由 AE2 官方转换器注册表处理。
	 *
	 * @param ingredient JEI 侧化学品条目
	 * @return 对应的 AE2 GenericStack；入参不是非空化学品栈时返回 null
	 */
	@Nullable
	public static GenericStack fromJeiIngredient(@Nullable Object ingredient) {
		if (!(ingredient instanceof ChemicalStack chemicalStack) || chemicalStack.isEmpty()) {
			return null;
		}
		try {
			MekanismKey key = MekanismKey.of(chemicalStack);
			return key == null ? null : new GenericStack(key, chemicalStack.getAmount());
		} catch (Throwable error) {
			ExtendedAEPlus.LOGGER.warn("Failed to convert Mekanism chemical stack to AE2 stack", error);
			return null;
		}
	}
}
