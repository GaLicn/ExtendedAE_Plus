package com.extendedae_plus.client;

import appeng.client.InitScreens;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.screen.EntitySpeedTickerScreen;
import com.extendedae_plus.api.ids.EAPComponents;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModelProvider;
import com.extendedae_plus.client.screen.SuperAssemblerMatrixScreen;
import com.extendedae_plus.client.screen.TagInventoryMEInterfaceScreen;
import com.extendedae_plus.client.screen.SuperCrystalAssemblerScreen;
import com.extendedae_plus.client.screen.SuperCircuitCutterScreen;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.hooks.BuiltInModelHooks;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.init.ModMenuTypes;
import com.extendedae_plus.items.BasicCoreItem;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import com.extendedae_plus.client.screen.LabeledWirelessTransceiverScreen;
import com.extendedae_plus.menu.LabeledWirelessTransceiverMenu;
import com.extendedae_plus.menu.TagInventoryMEInterfaceMenu;
import com.extendedae_plus.menu.SuperCrystalAssemblerMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import com.extendedae_plus.client.screen.GlobalProviderModesScreen;
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
        // AE2 26.1.10 registers crafting-unit model providers through its model event.
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        // 菜单 -> 屏幕 绑定（显式 ScreenConstructor，避免泛型推断问题）
        event.register(
                ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(),
                new MenuScreens.ScreenConstructor<
                        NetworkPatternControllerMenu,
                        GlobalProviderModesScreen>() {
                    @Override
                    public GlobalProviderModesScreen create(
                            NetworkPatternControllerMenu menu,
                            Inventory inv,
                            Component title) {
                        return new GlobalProviderModesScreen(menu, inv, title);
                    }
                }
        );

        event.register(
                ModMenuTypes.LABELED_WIRELESS_TRANSCEIVER.get(),
                new MenuScreens.ScreenConstructor<
                        LabeledWirelessTransceiverMenu,
                        LabeledWirelessTransceiverScreen>() {
                    @Override
                    public LabeledWirelessTransceiverScreen create(LabeledWirelessTransceiverMenu menu, Inventory inv, Component title) {
                        return new LabeledWirelessTransceiverScreen(menu, inv, title);
                    }
                }
        );

        event.register(
                ModMenuTypes.TAG_INVENTORY_ME_INTERFACE.get(),
                new MenuScreens.ScreenConstructor<
                        TagInventoryMEInterfaceMenu,
                        TagInventoryMEInterfaceScreen>() {
                    @Override
                    public TagInventoryMEInterfaceScreen create(TagInventoryMEInterfaceMenu menu,
                            Inventory inv,
                            Component title) {
                        return new TagInventoryMEInterfaceScreen(menu, inv, title);
                    }
                }
        );

        /**
         * 注册由 AE2 InitScreens 所需的屏幕资源映射（用于内置 JSON 屏幕注册）
         */
        InitScreens.register(event, ModMenuTypes.ENTITY_TICKER_MENU.get(), EntitySpeedTickerScreen::new, "/screens/entity_speed_ticker.json");
        InitScreens.register(event, ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), SuperAssemblerMatrixScreen::new,
                "/screens/super_assembler_matrix.json");
        InitScreens.register(event, ModMenuTypes.CRYSTAL_ASSEMBLER_PLUS.get(), SuperCrystalAssemblerScreen::new,
                "/screens/crystal_assembler_plus.json");
        InitScreens.register(event, ModMenuTypes.CIRCUIT_CUTTER_PLUS.get(), SuperCircuitCutterScreen::new,
                "/screens/circuit_cutter_plus.json");
    }
}
