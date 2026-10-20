package com.extendedae_plus;

import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.integration.jei.JeiClientBootstrap;
import com.extendedae_plus.util.ModCheckUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ExtendedAEPlus.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public class ExtendedAEPlusClient {
	public ExtendedAEPlusClient(ModContainer container, IEventBus modEventBus) {

		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

		// 注册按键绑定
		modEventBus.addListener((RegisterKeyMappingsEvent event) -> {
			event.register(ModKeybindings.CREATE_PATTERN_KEY);
			event.register(ModKeybindings.FILL_SEARCH_KEY);
			event.register(ModKeybindings.FORCE_CRAFT_KEY);
		});
	}

	@SubscribeEvent
	static void onClientSetup(FMLClientSetupEvent event) {
		// 真实 JEI 或 EMI 任一在场才注册输入监听。InputEvents 及其分派到的
		// CtrlQPatternKeyHandler 方法体内引用查看器实现类，条件不成立时不注册，
		// 也就不会触碰缺失模组的类。
		if (ModCheckUtils.isRealJeiLoaded() || ModCheckUtils.isLoaded(ModCheckUtils.MODID_EMI)) {
			JeiClientBootstrap.register();
		}
	}
}
