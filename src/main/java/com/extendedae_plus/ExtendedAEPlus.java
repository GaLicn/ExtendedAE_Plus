package com.extendedae_plus;

import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.client.ClientRegistrar;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.init.*;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * ExtendedAE Plus 主mod类
 */
@Mod("extendedae_plus")
public class ExtendedAEPlus {

    public static final String MODID = "extendedae_plus";

    // 注意：避免在静态初始化阶段访问注册对象，相关客户端注册改在 FMLClientSetupEvent 中执行。

    public ExtendedAEPlus() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 客户端的内置模型注册将在客户端事件阶段执行（见 ClientModEvents），不要在构造器中提前执行

        // 注册mod初始化事件
        modEventBus.addListener(this::commonSetup);

        // 注册方块与方块实体
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        // 在注册阶段将创造模式标签页放入注册表
        ModCreativeTabs.TABS.register(modEventBus);

        ModMenuTypes.MENUS.register(modEventBus);

        // 注册到Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);
        // 注册通用配置
        ModConfig.init();
//        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);
    }

    /**
     * 通用初始化设置
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册本模组网络通道与数据包
        event.enqueueWork(() -> {
            // 注册升级卡
            new UpgradeCards(event);
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

    /**
     * 客户端专用事件订阅类。
     * 完成客户端相关的延迟注册操作（如菜单界面绑定、渲染器注册、模型加载等），确保这些操作只在客户端执行，避免服务端崩溃。
     */
    @Mod.EventBusSubscriber(
            modid = ExtendedAEPlus.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT
    )
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(final FMLClientSetupEvent event) {
            // 直接在此处执行客户端一次性注册（UI/屏幕/渲染器绑定）
            // 注册客户端配置界面
//            ClientRegistrar.registerConfigScreen();

            // 将 InitScreens 的注册委托给 ClientRegistrar，便于集中管理客户端注册逻辑
            ClientRegistrar.registerInitScreens();

            // 菜单 -> 屏幕 绑定
            ClientRegistrar.registerMenuScreens();
        }

        @SubscribeEvent
        public static void onRegisterGeometryLoaders(final ModelEvent.RegisterGeometryLoaders evt) {
            try {
                ClientRegistrar.initBuiltInModels();
                // 注册 AE2 部件模型（例如 entity_ticker_part_item），仿照 CrazyAddons 的做法
                ModItems.registerPartModels();
            } catch (Exception ignored) {}
        }
    }
}
