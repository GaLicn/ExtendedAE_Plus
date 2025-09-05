package com.extendedae_plus.client;

import appeng.client.render.crafting.CraftingCubeModel;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModelProvider;
import com.extendedae_plus.client.screen.GlobalProviderModesScreen;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.hooks.BuiltInModelHooks;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
    }

    /**
     * 客户端设置阶段：延迟执行需要访问注册对象的客户端注册。
     */
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 确保在首次资源加载前完成内置模型注册（REGISTERED 保护避免重复）
            init();
            // 菜单 -> 屏幕 绑定
            MenuScreens.register(ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(), GlobalProviderModesScreen::new);
        });
    }
}
