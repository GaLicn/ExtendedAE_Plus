package com.extendedae_plus.init;

import appeng.menu.implementations.MenuTypeBuilder;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.menu.EntitySpeedTickerMenu;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlockEntity;
import com.extendedae_plus.content.cutter.SuperCircuitCutterBlockEntity;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import com.extendedae_plus.menu.LabeledWirelessTransceiverMenu;
import com.extendedae_plus.menu.SuperAssemblerMatrixMenu;
import com.extendedae_plus.menu.TagInventoryMEInterfaceMenu;
import com.extendedae_plus.menu.SuperCrystalAssemblerMenu;
import com.extendedae_plus.menu.SuperCircuitCutterMenu;
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

    public static final DeferredHolder<MenuType<?>, MenuType<TagInventoryMEInterfaceMenu>> TAG_INVENTORY_ME_INTERFACE =
            MENUS.register("tag_inventory_me_interface",
                    () -> IMenuTypeExtension.create(TagInventoryMEInterfaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EntitySpeedTickerMenu>> ENTITY_TICKER_MENU =
            MENUS.register("entity_speed_ticker",
                    () -> MenuTypeBuilder
                            .create(EntitySpeedTickerMenu::new, EntitySpeedTickerPart.class)
                            .build("entity_speed_ticker"));

    public static final DeferredHolder<MenuType<?>, MenuType<SuperAssemblerMatrixMenu>> SUPER_ASSEMBLER_MATRIX =
            MENUS.register("super_assembler_matrix",
                    () -> MenuTypeBuilder
                            .create(SuperAssemblerMatrixMenu::new, SuperAssemblerMatrixBlockEntity.class)
                            .build("super_assembler_matrix"));

    public static final DeferredHolder<MenuType<?>, MenuType<SuperCrystalAssemblerMenu>> CRYSTAL_ASSEMBLER_PLUS =
            MENUS.register("crystal_assembler_plus",
                    () -> MenuTypeBuilder
                            .create(SuperCrystalAssemblerMenu::new, SuperCrystalAssemblerBlockEntity.class)
                            .build("crystal_assembler_plus"));
    public static final DeferredHolder<MenuType<?>, MenuType<SuperCircuitCutterMenu>> CIRCUIT_CUTTER_PLUS =
            MENUS.register("circuit_cutter_plus",
                    () -> MenuTypeBuilder
                            .create(SuperCircuitCutterMenu::new, SuperCircuitCutterBlockEntity.class)
                            .build("circuit_cutter_plus"));
}
