package com.extendedae_plus.integration.jei;

public final class JeiClientBootstrap {
	private JeiClientBootstrap() {}

	public static void register() {
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.InputEvents::onMouseButtonPre);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.InputEvents::onMouseButtonReleasedPre);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.InputEvents::onKeyPressedPre);
		// Ctrl+Q 配方书签直接引用 JEI 类，仅在 JEI 在场时注册；lambda 体条件执行，类加载随之延迟。
		if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
			net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.extendedae_plus.client.event.CtrlQPatternKeyHandler::onScreenKeyPressed);
		}
	}
} 
