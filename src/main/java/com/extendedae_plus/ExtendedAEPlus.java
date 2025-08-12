package com.extendedae_plus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModBlocks;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;

/**
 * ExtendedAE Plus 主mod类
 */
@Mod("extendedae_plus")
public class ExtendedAEPlus {

    public static final String MODID = "extendedae_plus";

    public ExtendedAEPlus() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册mod初始化事件
        modEventBus.addListener(this::commonSetup);
        
        // 注册方块与方块实体
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        
        // 注册到Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册通用配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);
    }
    
    /**
     * 通用初始化设置
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 现已改用 ExtendedAE 的 EPPNetworkHandler + CGenericPacket，无需自定义网络初始化
    }
}
