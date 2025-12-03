package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExtendedAEPlus.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ExtendedAEPlus.MODID + ".main"))
                    .icon(() -> ModItems.WIRELESS_TRANSCEIVER.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        List.of(
                                ModItems.WIRELESS_TRANSCEIVER.get().getDefaultInstance(),
                                ModItems.NETWORK_PATTERN_CONTROLLER.get().getDefaultInstance(),
                                ModItems.ACCELERATOR_4x.get().getDefaultInstance(),
                                ModItems.ACCELERATOR_16x.get().getDefaultInstance(),
                                ModItems.ACCELERATOR_64x.get().getDefaultInstance(),
                                ModItems.ACCELERATOR_256x.get().getDefaultInstance(),
                                ModItems.ACCELERATOR_1024x.get().getDefaultInstance(),
                                ModItems.ASSEMBLER_MATRIX_UPLOAD_CORE.get().getDefaultInstance(),
                                ModItems.CHANNEL_CARD.get().getDefaultInstance(),
                                ModItems.VIRTUAL_CRAFTING_CARD.get().getDefaultInstance(),
                                ModItems.ENTITY_TICKER_PART_ITEM.get().getDefaultInstance(),
                                ModItems.INFINITY_BIGINTEGER_CELL_ITEM.get().getDefaultInstance(),
                                ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get().getDefaultInstance(),
                                ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get().getDefaultInstance(),
                                ModItems.ASSEMBLER_MATRIX_PATTERN_PLUS.get().getDefaultInstance()
                        ).forEach(output::accept);

                        // 放入四个预设的 stacks（x2,x4,x8,x16），使用 ModItems 工厂创建
                        for (byte multiplier : new byte[] {2, 4, 8, 16}) {
                            ItemStack stack = ModItems.createEntitySpeedCardStack(multiplier);
                            output.accept(stack);
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
