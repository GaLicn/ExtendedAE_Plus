package com.extendedae_plus;

import appeng.api.parts.IPart;
import appeng.api.parts.PartModels;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.items.parts.PartModelsHelper;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.init.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ExtendedAEPlus.MODID)
public class ExtendedAEPlus {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "extendedae_plus";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // 移除 MDK 示例注册，改为使用实际模组的方块/物品/创造物品栏注册见 ModBlocks、ModItems、ModCreativeTabs

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExtendedAEPlus(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        // 注册网络负载处理器（NeoForge 1.21 新式 Payload API）
        modEventBus.addListener(ModNetwork::registerPayloadHandlers);
        // 注册能力：让 AE2 电缆识别我们的 In-World Grid Node Host
        modEventBus.addListener(ModCapabilities::onRegisterCapabilities);

        // 注册本模组方块/物品/创造物品栏
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so menu types get registered
        ModMenuTypes.MENUS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExtendedAEPlus) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // 注册配置：接入自定义的 ModConfigs
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);
    }

    // 便捷 ResourceLocation 工具
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        // 示例日志，避免引用不存在的模板 Config 字段
        LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        // 绑定 AE2 的 CraftingBlockEntity 到本模组的自定义加速器方块，避免 AEBaseEntityBlock.blockEntityType 为空
        event.enqueueWork(() -> {
            try {
                // 注册升级卡
                new UpgradeCards(event);

                // 为 PartItem 注册 AE2 部件模型
                PartModels.registerModels(
                        PartModelsHelper.createModels(
                                ModItems.ENTITY_TICKER_PART_ITEM.get().getPartClass().asSubclass(IPart.class)
                        )
                );
                
                // 注册自定义 AE2 MenuLocator（用于 Curios 槽位打开菜单）
                try {
                    appeng.menu.locator.MenuLocators.register(
                            com.extendedae_plus.menu.locator.CuriosItemLocator.class,
                            com.extendedae_plus.menu.locator.CuriosItemLocator::writeToPacket,
                            com.extendedae_plus.menu.locator.CuriosItemLocator::readFromPacket
                    );
                    LOGGER.info("Registered AE2 MenuLocator: CuriosItemLocator");
                } catch (Throwable t) {
                    LOGGER.warn("Failed to register CuriosItemLocator with AE2 MenuLocators: {}", t.toString());
                }

                AEBaseEntityBlock<CraftingBlockEntity> b4 = (AEBaseEntityBlock<CraftingBlockEntity>) ModBlocks.ACCELERATOR_4x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b16 = (AEBaseEntityBlock<CraftingBlockEntity>) ModBlocks.ACCELERATOR_16x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b64 = (AEBaseEntityBlock<CraftingBlockEntity>) ModBlocks.ACCELERATOR_64x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b256 = (AEBaseEntityBlock<CraftingBlockEntity>) ModBlocks.ACCELERATOR_256x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b1024 = (AEBaseEntityBlock<CraftingBlockEntity>) ModBlocks.ACCELERATOR_1024x.get();

                // 使用我们自定义的 CraftingBlockEntity 类型，它的有效方块列表包含自定义加速器
                var type = ModBlockEntities.EPLUS_CRAFTING_UNIT_BE.get();
                // 不提供专用 ticker（AE2 会在其注册时按接口注入），此处传 null 即可
                b4.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                b16.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                b64.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                b256.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                b1024.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                LOGGER.info("Bound AE2 CraftingBlockEntity to ExtendedAE Plus accelerators.");
            } catch (Throwable t) {
                LOGGER.warn("Failed to bind CraftingBlockEntity to accelerators: {}", t.toString());
            }
        });
    }

    // 移除示例 addCreative 事件，避免将示例物品加入原版标签

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}

