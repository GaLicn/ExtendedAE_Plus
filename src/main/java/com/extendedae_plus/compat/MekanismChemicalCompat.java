package com.extendedae_plus.compat;

import appeng.api.stacks.GenericStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;

/**
 * Mekanism 化学品 → AE2 {@link GenericStack} 的桥接（依赖 Applied Mekanistics 提供的 AE2 键类型）。
 * <p>
 * 本类直接引用 mekanism / appmek 的类，加载即需要两者在场，
 * 因此只能在 {@link MekanismChemicalGate#isAvailable()} 为真之后调用 ——
 * 判定必须放在不引用这些类的独立类里，否则光是查询可用性就会触发本类的类解析。
 * <p>
 * 量纲：Mekanism 的 {@code ChemicalStack} 与 AppMek 的 {@code MekanismKeyType}
 * 都以 mB 为单位（{@code getAmountPerUnit() == 1000}），因此数量 1:1 直传，
 * 不像流体那样需要 EMI droplets ÷ 81 的换算。
 */
public final class MekanismChemicalCompat {

	private MekanismChemicalCompat() {}

	/**
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
