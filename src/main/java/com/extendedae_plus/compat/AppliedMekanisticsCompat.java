package com.extendedae_plus.compat;

import me.ramidzkh.mekae2.ae2.MekanismKey;

public final class AppliedMekanisticsCompat {
	private AppliedMekanisticsCompat() {
	}

	public static void addBookmark(Object key) {
		if (key instanceof MekanismKey mekanismKey) {
			JeiRuntimeCompat.addBookmark(mekanismKey.getStack());
		}
	}
}
