package com.extendedae_plus.datagen;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/**
 * ExtendedAE Plus 数据生成事件总线
 * 用于 NeoForge 1.21.1
 */
@EventBusSubscriber(modid = ExtendedAEPlus.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 注册配方提供器
        generator.addProvider(
                event.includeServer(),
                new CrafterRecipe(generator.getPackOutput(), lookupProvider)
        );


    }
}
