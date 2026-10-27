package com.extendedae_plus.integration.jei;

import com.extendedae_plus.client.InputEvents;
import com.extendedae_plus.client.event.CtrlQPatternKeyHandler;
import com.extendedae_plus.client.event.EmiCtrlQHandler;
import com.extendedae_plus.util.ModCheckUtils;
import net.neoforged.neoforge.common.NeoForge;

public final class JeiClientBootstrap {
	private JeiClientBootstrap() {}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(InputEvents::onMouseButtonPre);
		NeoForge.EVENT_BUS.addListener(InputEvents::onMouseButtonReleasedPre);
		NeoForge.EVENT_BUS.addListener(InputEvents::onKeyPressedPre);
		// Ctrl+Q 配方书签直接引用 JEI 类，仅在真实 JEI 在场时注册；
		// TMRV 会冒充 jei modId 却缺少实现类，所以不能用 isLoaded(MODID_JEI)。
		if (ModCheckUtils.isRealJeiLoaded()) {
			NeoForge.EVENT_BUS.addListener(CtrlQPatternKeyHandler::onScreenKeyPressed);
		}
		// Ctrl+Q 快速创建样板的 EMI 分支：仅引用 dev.emi 惰性解析类，EMI 在场时注册。
		if (ModCheckUtils.isLoaded(ModCheckUtils.MODID_EMI)) {
			NeoForge.EVENT_BUS.addListener(EmiCtrlQHandler::onScreenKeyPressed);
		}
	}
}
