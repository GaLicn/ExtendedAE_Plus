package com.extendedae_plus.compat;

import net.neoforged.fml.ModList;

/**
 * {@link MekanismChemicalCompat} 的可用性判定。
 * <p>
 * 单独成类是必要的：判定方法若与化学品转换写在同一个类里，
 * 光是调用判定就会加载那个类，从而要求 mekanism / appmek 的类可解析——
 * 只装 Mekanism 不装 Applied Mekanistics 的环境会直接崩。本类只读 ModList。
 */
public final class MekanismChemicalGate {
	private static Boolean available;

	private MekanismChemicalGate() {}

	public static boolean isAvailable() {
		if (available == null) {
			try {
				ModList modList = ModList.get();
				available = modList != null && modList.isLoaded("mekanism") && modList.isLoaded("appmek");
			} catch (Throwable t) {
				available = false;
			}
		}
		return available;
	}
}
