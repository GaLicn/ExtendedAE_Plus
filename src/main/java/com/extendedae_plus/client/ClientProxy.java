package com.extendedae_plus.client;

import appeng.client.render.crafting.CraftingCubeModel;
import appeng.init.client.InitScreens;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import com.extendedae_plus.client.screen.LabeledWirelessTransceiverScreen;
import com.extendedae_plus.menu.LabeledWirelessTransceiverMenu;
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
    private static boolean REGISTERED = false;

    private ClientProxy() {}

    public static void init() {
        if (REGISTERED) return;
        REGISTERED = true;
        // 注册 Item property
        ItemProperties.register(ModItems.ENTITY_SPEED_CARD.get(), ExtendedAEPlus.id("mult"),
                (stack, world, entity, seed) -> (float) EntitySpeedCardItem.readMultiplier(stack));
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

        event.register(
                ModMenuTypes.LABELED_WIRELESS_TRANSCEIVER.get(),
                new net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<
                        LabeledWirelessTransceiverMenu,
                        LabeledWirelessTransceiverScreen>() {
                    @Override
                    public LabeledWirelessTransceiverScreen create(LabeledWirelessTransceiverMenu menu, net.minecraft.world.entity.player.Inventory inv, net.minecraft.network.chat.Component title) {
                        return new LabeledWirelessTransceiverScreen(menu, inv, title);
                    }
                }
        );

        /**
         * 注册由 AE2 InitScreens 所需的屏幕资源映射（用于内置 JSON 屏幕注册）
         */
        InitScreens.register(event, ModMenuTypes.ENTITY_TICKER_MENU.get(), EntitySpeedTickerScreen::new, "/screens/entity_speed_ticker.json");
    }
}
