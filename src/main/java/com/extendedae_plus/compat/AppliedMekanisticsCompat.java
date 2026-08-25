package com.extendedae_plus.compat;

import appeng.api.stacks.GenericStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;

/**
 * Applied Mekanistics 的桥接。
 * <p>
 * 本类直接引用 mekanism / appmek 的类，加载即需要两者在场，
 * 因此只能在 {@link com.extendedae_plus.util.ModCheckUtils#isAppMekLoading()} 为真之后调用。
 */
public final class AppliedMekanisticsCompat {
	private AppliedMekanisticsCompat() {
	}

	public static void addBookmark(Object key) {
		if (key instanceof MekanismKey mekanismKey) {
			JeiRuntimeCompat.addBookmark(mekanismKey.getStack());
		}
	}

	/**
	 * Mekanism 化学品 → AE2 {@link GenericStack}。
	 * <p>
	 * 量纲：Mekanism 的 {@link ChemicalStack} 与 AppMek 的 {@code MekanismKeyType}
	 * 都以 mB 为单位（{@code getAmountPerUnit() == 1000}），因此数量 1:1 直传，
	 * 不像流体那样需要 EMI droplets ÷ 81 的换算。
	 *
	 * @param emiKey EMI 栈的 key（Mekanism 的 ChemicalEmiStack 会返回 {@link Chemical}）
	 * @param amount EMI 栈的数量，单位 mB
	 * @return 对应的 AE2 GenericStack；key 不是化学品时返回 null
	 */
	public static GenericStack toGenericStack(Object emiKey, long amount) {
		try {
			if (!(emiKey instanceof Chemical chemical) || chemical.isEmptyType()) {
				return null;
			}
			long mb = Math.max(1, amount);
			MekanismKey key = MekanismKey.of(new ChemicalStack(chemical, mb));
			return key == null ? null : new GenericStack(key, mb);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
