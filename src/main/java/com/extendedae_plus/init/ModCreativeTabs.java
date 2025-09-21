package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExtendedAEPlus.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
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
                        // 放入四个预设的 stacks（x2,x4,x8,x16），使用 ModItems 工厂创建
                        output.accept(ModItems.createEntitySpeedCardStack((byte) 2));
                        output.accept(ModItems.createEntitySpeedCardStack((byte) 4));
                        output.accept(ModItems.createEntitySpeedCardStack((byte) 8));
                        output.accept(ModItems.createEntitySpeedCardStack((byte) 16));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
