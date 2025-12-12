package com.extendedae_plus.init;

import appeng.menu.implementations.MenuTypeBuilder;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import com.extendedae_plus.menu.LabeledWirelessTransceiverMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ExtendedAEPlus.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkPatternControllerMenu>> NETWORK_PATTERN_CONTROLLER =
            MENUS.register("network_pattern_controller",
                    () -> IMenuTypeExtension.create(NetworkPatternControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LabeledWirelessTransceiverMenu>> LABELED_WIRELESS_TRANSCEIVER =
            MENUS.register("labeled_wireless_transceiver",
                    () -> IMenuTypeExtension.create(LabeledWirelessTransceiverMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EntitySpeedTickerMenu>> ENTITY_TICKER_MENU =
            MENUS.register("entity_speed_ticker",
                    () -> MenuTypeBuilder
                            .create(EntitySpeedTickerMenu::new, EntitySpeedTickerPart.class)
                            .build("entity_speed_ticker"));
}
