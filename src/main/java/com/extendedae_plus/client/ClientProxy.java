package com.extendedae_plus.client;

import appeng.client.render.crafting.CraftingCubeModel;
import appeng.init.client.InitScreens;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModelProvider;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.hooks.BuiltInModelHooks;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端模型注册，将 formed 模型注册为内置模型。
 */
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class ClientProxy {
    private ClientProxy() {}

    private static boolean REGISTERED = false;

    public static void init() {
        if (REGISTERED) return;
        REGISTERED = true;
        // 注册 Item property，用于根据 ItemStack 的 NBT exponent 切换模型
        ItemProperties.register(ModItems.ENTITY_SPEED_CARD.get(), ExtendedAEPlus.id("mult"),
                (stack, world, entity, seed) -> (float) EntitySpeedCardItem.readMultiplier(stack));

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

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        // 菜单 -> 屏幕 绑定（显式 ScreenConstructor，避免泛型推断问题）
        event.register(
                ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(),
                new net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<
                        com.extendedae_plus.menu.NetworkPatternControllerMenu,
                        com.extendedae_plus.client.screen.GlobalProviderModesScreen>() {
                    @Override
                    public com.extendedae_plus.client.screen.GlobalProviderModesScreen create(
                            com.extendedae_plus.menu.NetworkPatternControllerMenu menu,
                            net.minecraft.world.entity.player.Inventory inv,
                            net.minecraft.network.chat.Component title) {
                        return new com.extendedae_plus.client.screen.GlobalProviderModesScreen(menu, inv, title);
                    }
                }
        );

        /**
         * 注册由 AE2 InitScreens 所需的屏幕资源映射（用于内置 JSON 屏幕注册）
         */
        InitScreens.register(event, ModMenuTypes.ENTITY_TICKER_MENU.get(), EntitySpeedTickerScreen<EntitySpeedTickerMenu>::new, "/screens/entity_speed_ticker.json");
    }
}
