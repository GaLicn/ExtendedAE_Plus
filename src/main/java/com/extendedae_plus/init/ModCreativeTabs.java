package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
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
                    })
                    .build());
}
