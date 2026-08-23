package com.extendedae_plus;

import com.extendedae_plus.client.ModKeybindings;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModList;

@Mod(value = ExtendedAEPlus.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public class ExtendedAEPlusClient {
	public ExtendedAEPlusClient(ModContainer container, IEventBus modEventBus) {

		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		
		// 注册按键绑定
		modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
			event.register(ModKeybindings.CREATE_PATTERN_KEY);
			event.register(ModKeybindings.FILL_SEARCH_KEY);
		});
	}

	@SubscribeEvent
	static void onClientSetup(FMLClientSetupEvent event) {
		ExtendedAEPlus.LOGGER.info("HELLO FROM CLIENT SETUP");
		ExtendedAEPlus.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

		// EMI 或 JEI 任一存在即注册输入监听；InputEvents 内部按查看器来源自守卫，
		// 未安装对应查看器时相关分支不会被触发，避免触碰缺失模组的类导致类加载失败。
		if (ModList.get().isLoaded("jei") || ModList.get().isLoaded("emi")) {
			try {
				Class<?> bootstrap = Class.forName("com.extendedae_plus.integration.jei.JeiClientBootstrap");
				java.lang.reflect.Method m = bootstrap.getMethod("register");
				m.invoke(null);
			} catch (Throwable t) {
				ExtendedAEPlus.LOGGER.warn("Failed to register EMI/JEI client listeners: {}", t.toString());
			}
		}
	}
}
