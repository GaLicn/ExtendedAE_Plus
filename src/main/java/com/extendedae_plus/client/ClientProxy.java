package com.extendedae_plus.client;

import appeng.client.render.crafting.CraftingCubeModel;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModelProvider;
import com.extendedae_plus.client.screen.GlobalProviderModesScreen;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.hooks.BuiltInModelHooks;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * 客户端模型注册，将 formed 模型注册为内置模型。
 */
public final class ClientProxy {
    private ClientProxy() {}

    private static boolean REGISTERED = false;

    public static void init() {
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

        // 菜单 -> 屏幕 绑定
        MenuScreens.register(ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(), GlobalProviderModesScreen::new);
    }

    /**
     * 客户端设置阶段：延迟执行需要访问注册对象的客户端注册。
     */
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            init();
            registerConfigScreen();
        });
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
                        (mc, parent) -> new com.extendedae_plus.client.ModConfigScreen(parent))
        );
    }
}
