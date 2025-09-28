package com.extendedae_plus;

import appeng.api.storage.StorageCells;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import com.extendedae_plus.ae.api.storage.InfinityBigIntegerCellHandler;
import com.extendedae_plus.ae.api.storage.InfinityBigIntegerCellInventory;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.init.*;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(ExtendedAEPlus.MODID)
public class ExtendedAEPlus {
    public static final String MODID = "extendedae_plus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExtendedAEPlus(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetwork::registerPayloadHandlers);
        modEventBus.addListener(ModCapabilities::onRegisterCapabilities);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(ExtendedAEPlus::onLevelLoad);

        NeoForge.EVENT_BUS.addListener(InfinityBigIntegerCellInventory::onServerTick);
        NeoForge.EVENT_BUS.addListener(InfinityBigIntegerCellInventory::onServerStopping);

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        StorageCells.addCellHandler(InfinityBigIntegerCellHandler.INSTANCE);
        event.enqueueWork(() -> {
            try {
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

                AEBaseEntityBlock<CraftingBlockEntity> b4 = ModBlocks.ACCELERATOR_4x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b16 = ModBlocks.ACCELERATOR_16x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b64 = ModBlocks.ACCELERATOR_64x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b256 = ModBlocks.ACCELERATOR_256x.get();
                AEBaseEntityBlock<CraftingBlockEntity> b1024 = ModBlocks.ACCELERATOR_1024x.get();

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

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }


    // 在世界加载时注册/加载 SavedData
    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            InfinityStorageManager.getForLevel(serverLevel);
        }
    }
}

