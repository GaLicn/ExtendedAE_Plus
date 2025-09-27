package com.extendedae_plus;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModList;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExtendedAEPlus.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public class ExtendedAEPlusClient {
	public ExtendedAEPlusClient(ModContainer container) {
		// Allows NeoForge to create a config screen for this mod's configs.
		// The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
		// Do not forget to add translations for your config options to the en_us.json file.
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
	}

	@SubscribeEvent
	static void onClientSetup(FMLClientSetupEvent event) {
		// Some client setup code
		ExtendedAEPlus.LOGGER.info("HELLO FROM CLIENT SETUP");
		ExtendedAEPlus.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

		// Register JEI-dependent input handlers only when JEI is present
		if (ModList.get().isLoaded("jei")) {
			try {
				Class<?> bootstrap = Class.forName("com.extendedae_plus.integration.jei.JeiClientBootstrap");
				java.lang.reflect.Method m = bootstrap.getMethod("register");
				m.invoke(null);
			} catch (Throwable t) {
				ExtendedAEPlus.LOGGER.warn("Failed to register JEI client listeners: {}", t.toString());
			}
		}
	}
}
