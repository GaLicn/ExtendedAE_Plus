package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExtendedAEPlus.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ExtendedAEPlus.MODID + ".main"))
                    .icon(() -> ModItems.WIRELESS_TRANSCEIVER.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // 将本模组物品加入创造物品栏
                        output.accept(ModItems.WIRELESS_TRANSCEIVER.get());
                        output.accept(ModItems.NETWORK_PATTERN_CONTROLLER.get());
                        output.accept(ModItems.ACCELERATOR_4x.get());
                        output.accept(ModItems.ACCELERATOR_16x.get());
                        output.accept(ModItems.ACCELERATOR_64x.get());
                        output.accept(ModItems.ACCELERATOR_256x.get());
                        output.accept(ModItems.ACCELERATOR_1024x.get());
                        output.accept(ModItems.ENTITY_TICKER_PART_ITEM.get());
                    })
                    .build());
}
