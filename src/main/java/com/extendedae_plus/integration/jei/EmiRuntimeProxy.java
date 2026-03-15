package com.extendedae_plus.integration.jei;

import net.neoforged.fml.ModList;

public final class EmiRuntimeProxy {
	public static boolean isInstalled = ModList.get().isLoaded("emi");
}
