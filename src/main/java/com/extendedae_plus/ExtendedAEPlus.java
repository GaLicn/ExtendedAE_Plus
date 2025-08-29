package com.extendedae_plus;

import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModBlocks;
import com.extendedae_plus.init.ModCreativeTabs;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.extendedae_plus.network.ModNetwork;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.resources.ResourceLocation;

import com.extendedae_plus.client.ClientProxy;
import com.extendedae_plus.client.ModConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * ExtendedAE Plus 主mod类
 */
@Mod("extendedae_plus")
public class ExtendedAEPlus {

    public static final String MODID = "extendedae_plus";

    // 在类加载时（尽可能早）在客户端注册内置模型，避免首次资源加载时错过。
    static {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            System.out.println("[ExtendedAE_Plus] Static init: register built-in models");
            ClientProxy.init();
        });
    }

    public ExtendedAEPlus() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册mod初始化事件
        modEventBus.addListener(this::commonSetup);
        
        // 注册方块与方块实体
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        
        // 注册到Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册通用配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);

        // 构造期在客户端再确保一次注册（幂等）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientProxy::init);

        // 在 Mods 菜单中注册配置界面入口（仅客户端）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ModLoadingContext.get().registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (mc, parent) -> new ModConfigScreen(parent))
                )
        );
    }
    
    /**
     * 通用初始化设置
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册本模组网络通道与数据包
        event.enqueueWork(() -> {
            ModNetwork.register();
            // 注册自定义 Curios 宿主定位器，便于将菜单宿主信息在服务端与客户端间同步
            MenuLocators.register(CuriosItemLocator.class, CuriosItemLocator::writeToPacket, CuriosItemLocator::readFromPacket);
        });
    }

    /**
     * 便捷方法：生成 ResourceLocation
     */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
