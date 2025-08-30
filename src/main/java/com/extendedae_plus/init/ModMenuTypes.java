package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ExtendedAEPlus.MODID);

    public static final RegistryObject<MenuType<NetworkPatternControllerMenu>> NETWORK_PATTERN_CONTROLLER =
            MENUS.register("network_pattern_controller",
                    () -> IForgeMenuType.create(NetworkPatternControllerMenu::new));
}
