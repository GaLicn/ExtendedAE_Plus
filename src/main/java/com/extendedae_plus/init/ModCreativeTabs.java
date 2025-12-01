package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
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
                        // 装配矩阵上传核心
                        output.accept(ModItems.ASSEMBLER_MATRIX_UPLOAD_CORE.get());
                        //超级装配矩阵速度核心
                        output.accept(ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get());
                        //超级装配矩阵合成核心
                        output.accept(ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get());

                        //实体加速器&加速卡
                        output.accept(ModItems.CRAFTING_ACCELERATOR_4x.get());
                        output.accept(ModItems.CRAFTING_ACCELERATOR_16x.get());
                        output.accept(ModItems.CRAFTING_ACCELERATOR_64x.get());
                        output.accept(ModItems.CRAFTING_ACCELERATOR_256x.get());
                        output.accept(ModItems.CRAFTING_ACCELERATOR_1024x.get());
                        output.accept(ModItems.ENTITY_TICKER_PART_ITEM.get());

                        // 放入四个预设的 stacks（x2,x4,x8,x16），使用 ModItems 工厂创建
                        output.accept(ModItems.createEntitySpeedCardStack(2));
                        output.accept(ModItems.createEntitySpeedCardStack(4));
                        output.accept(ModItems.createEntitySpeedCardStack(8));
                        output.accept(ModItems.createEntitySpeedCardStack(16));
                        // 频道卡
                        output.accept(ModItems.CHANNEL_CARD.get());
                        output.accept(ModItems.VIRTUAL_CRAFTING_CARD.get());

                        output.accept(ModItems.OBLIVION_SINGULARITY.get());
                        output.accept(ModItems.BASIC_CORE.get());
                        output.accept(ModItems.STORAGE_CORE.get());
                        if (ModItems.ENERGY_STORAGE_CORE != null) {
                            output.accept(ModItems.ENERGY_STORAGE_CORE.get());
                        }
                        if (ModItems.QUANTUM_STORAGE_CORE != null) {
                            output.accept(ModItems.QUANTUM_STORAGE_CORE.get());
                        }
                        output.accept(ModItems.SPATIAL_CORE.get());
                        output.accept(ModItems.INFINITY_CORE.get());

                        output.accept(ModItems.INFINITY_BIGINTEGER_CELL.get());
                    })
                    .build());
}
