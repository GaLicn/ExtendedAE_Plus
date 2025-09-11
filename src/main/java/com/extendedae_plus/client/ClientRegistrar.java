package com.extendedae_plus.client;

import appeng.client.render.crafting.CraftingCubeModel;
import appeng.init.client.InitScreens;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModelProvider;
import com.extendedae_plus.client.screen.GlobalProviderModesScreen;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.hooks.BuiltInModelHooks;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 客户端模型注册，将 formed 模型注册为内置模型。
 */
public final class ClientRegistrar {
    private ClientRegistrar() {}

    private static boolean REGISTERED = false;

    /**
     * 注册内置模型（formed 模型等）。可被 ModelEvent 或启动阶段直接调用。
     */
    public static void initBuiltInModels() {
        if (REGISTERED) return;
        REGISTERED = true;
        // 注册四种形成态模型为内置模型
        BuiltInModelHooks.addBuiltInModel(
                ExtendedAEPlus.id("block/crafting/4x_accelerator_formed_v2"),
                new CraftingCubeModel(new EPlusCraftingCubeModelProvider(EPlusCraftingUnitType.ACCELERATOR_4x)));

        BuiltInModelHooks.addBuiltInModel(
                ExtendedAEPlus.id("block/crafting/16x_accelerator_formed_v2"),
                new CraftingCubeModel(new EPlusCraftingCubeModelProvider(EPlusCraftingUnitType.ACCELERATOR_16x)));

        BuiltInModelHooks.addBuiltInModel(
                ExtendedAEPlus.id("block/crafting/64x_accelerator_formed_v2"),
                new CraftingCubeModel(new EPlusCraftingCubeModelProvider(EPlusCraftingUnitType.ACCELERATOR_64x)));

        BuiltInModelHooks.addBuiltInModel(
                ExtendedAEPlus.id("block/crafting/256x_accelerator_formed_v2"),
                new CraftingCubeModel(new EPlusCraftingCubeModelProvider(EPlusCraftingUnitType.ACCELERATOR_256x)));

        BuiltInModelHooks.addBuiltInModel(
                ExtendedAEPlus.id("block/crafting/1024x_accelerator_formed_v2"),
                new CraftingCubeModel(new EPlusCraftingCubeModelProvider(EPlusCraftingUnitType.ACCELERATOR_1024x)));
    }

    /**
     * 将菜单类型与对应的屏幕绑定。
     */
    public static void registerMenuScreens() {
        MenuScreens.register(ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(), GlobalProviderModesScreen::new);
    }

    /**
     * 注册由 AE2 InitScreens 所需的屏幕资源映射（用于内置 JSON 屏幕注册）
     */
    public static void registerInitScreens() {
        InitScreens.register(ModMenuTypes.ENTITY_TICKER_MENU.get(),
                EntitySpeedTickerScreen<EntitySpeedTickerMenu>::new,
                "/screens/entity_speed_ticker.json");
    }

    /**
     * 仅客户端：在 Mods 菜单注册配置界面入口。
     * 将对 Screen 的引用限制在客户端侧，避免服务端类加载。
     */
    public static void registerConfigScreen() {
        // 将 ModConfigScreen 的引用放在此处，确保仅在 Dist.CLIENT 下解析该类
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ModConfigScreen(parent))
        );
    }
}
