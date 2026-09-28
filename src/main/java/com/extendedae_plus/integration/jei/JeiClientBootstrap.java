package com.extendedae_plus.integration.jei;

import com.extendedae_plus.client.InputEvents;
import com.extendedae_plus.client.event.CtrlQPatternKeyHandler;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

public final class JeiClientBootstrap {
	private JeiClientBootstrap() {}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(InputEvents::onMouseButtonPre);
		// EMI 26.1.2 发布后恢复松开事件监听。
		NeoForge.EVENT_BUS.addListener(InputEvents::onKeyPressedPre);
		// Ctrl+Q 配方书签直接引用 JEI 类，仅在 JEI 在场时注册；lambda 体条件执行，类加载随之延迟。
		if (ModList.get().isLoaded("jei")) {
			NeoForge.EVENT_BUS.addListener(CtrlQPatternKeyHandler::onScreenKeyPressed);
			NeoForge.EVENT_BUS.addListener(ExtendedAEJeiPlugin::onRecipesReceived);
			NeoForge.EVENT_BUS.addListener(ExtendedAEJeiPlugin::onLogout);
		}
		// EMI 26.1.2 发布后恢复 EMI Ctrl+Q 注册入口。
	}
} 
