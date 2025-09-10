package com.extendedae_plus;

import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.client.ClientProxy;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.*;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.extendedae_plus.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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

        // 在客户端尽早注册内置模型，保证首次资源加载前映射已建立（仿照 AE2 的 AppEngClient 构造期注册）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientProxy::initBuiltInModels);

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
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);

        // 客户端侧延迟注册：在 FMLClientSetupEvent 阶段执行（包含 MenuScreens 绑定等）
        // 由下面的 ClientModEvents 负责在客户端总线上接收事件并委派
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
            ClientProxy.registerConfigScreen();
            // 菜单 -> 屏幕 绑定
            ClientProxy.registerMenuScreens();
        }

        @SubscribeEvent
        public static void onRegisterGeometryLoaders(final ModelEvent.RegisterGeometryLoaders evt) {
            try {
                ClientProxy.initBuiltInModels();
            } catch (Exception ignored) {}
        }
    }
}
