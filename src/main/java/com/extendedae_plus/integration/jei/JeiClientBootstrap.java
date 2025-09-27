package com.extendedae_plus.integration.jei;

public final class JeiClientBootstrap {
	private JeiClientBootstrap() {}

	public static void register() {
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.InputEvents::onMouseButtonPre);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.InputEvents::onKeyPressedPre);
	}
} 