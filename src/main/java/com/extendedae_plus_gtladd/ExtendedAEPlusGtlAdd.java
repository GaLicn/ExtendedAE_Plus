package com.extendedae_plus_gtladd;

import com.extendedae_plus_gtladd.config.ModConfig;
import com.extendedae_plus_gtladd.init.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * ExtendedAE Plus 主mod类
 */
@Mod("extendedae_plus_gtladd")
public class ExtendedAEPlusGtlAdd {
    public static final String MODID = "extendedae_plus_gtladd";

    public ExtendedAEPlusGtlAdd() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModConfig.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation("extendedae_plus_gtladd", path);
    }
}