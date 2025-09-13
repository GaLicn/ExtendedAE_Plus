package com.extendedae_plus.init;

import appeng.api.parts.IPart;
import appeng.api.parts.PartModels;
import appeng.items.parts.PartModelsHelper;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.ae.items.EntitySpeedTickerPartItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ExtendedAEPlus.MODID);

    public static final RegistryObject<Item> WIRELESS_TRANSCEIVER = ITEMS.register(
            "wireless_transceiver",
            () -> new BlockItem(ModBlocks.WIRELESS_TRANSCEIVER.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> NETWORK_PATTERN_CONTROLLER = ITEMS.register(
            "network_pattern_controller",
            () -> new BlockItem(ModBlocks.NETWORK_PATTERN_CONTROLLER.get(), new Item.Properties())
    );

    // Crafting Accelerators
    public static final RegistryObject<Item> ACCELERATOR_4x = ITEMS.register(
            "4x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_4x.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> ACCELERATOR_16x = ITEMS.register(
            "16x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_16x.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> ACCELERATOR_64x = ITEMS.register(
            "64x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_64x.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> ACCELERATOR_256x = ITEMS.register(
            "256x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_256x.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> ACCELERATOR_1024x = ITEMS.register(
            "1024x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_1024x.get(), new Item.Properties())
    );

    public static final RegistryObject<EntitySpeedTickerPartItem> ENTITY_TICKER_PART_ITEM = ITEMS.register(
            "entity_speed_ticker",
                    () -> new EntitySpeedTickerPartItem(new Item.Properties())
    );

    // AE Upgrade Cards: 实体加速卡（四个等级：x2,x4,x8,x16）
    // 单一实体加速卡 Item（不同等级由 ItemStack.nbt 存储）
    public static final RegistryObject<EntitySpeedCardItem> ENTITY_SPEED_CARD = ITEMS.register(
            "entity_speed_card",
            () -> new EntitySpeedCardItem(new Item.Properties())
    );

    /**
     * 为 PartItem 注册 AE2 部件模型。
     * 在客户端进行模型/几何体注册时调用。
     */
    public static void registerPartModels() {
        PartModels.registerModels(
                PartModelsHelper.createModels(
                        ENTITY_TICKER_PART_ITEM.get().getPartClass().asSubclass(IPart.class)
                )
        );
    }

    /**
     * 工厂：创建带 multiplier 的实体加速卡 ItemStack（2/4/8/16）
     */
    public static net.minecraft.world.item.ItemStack createEntitySpeedCardStack(int multiplier) {
        return EntitySpeedCardItem.withMultiplier(multiplier);
    }
}
