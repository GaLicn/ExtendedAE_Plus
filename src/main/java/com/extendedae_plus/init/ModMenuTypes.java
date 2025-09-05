package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ExtendedAEPlus.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkPatternControllerMenu>> NETWORK_PATTERN_CONTROLLER =
            MENUS.register("network_pattern_controller",
                    () -> new MenuType<>(NetworkPatternControllerMenu::new));
}
