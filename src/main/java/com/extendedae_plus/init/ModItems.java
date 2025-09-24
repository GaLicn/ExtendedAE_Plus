package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.definitions.upgrades.EntitySpeedCardItem;
import com.extendedae_plus.ae.items.EntitySpeedTickerPartItem;
import com.extendedae_plus.ae.items.ChannelCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExtendedAEPlus.MODID);

    public static final DeferredItem<Item> WIRELESS_TRANSCEIVER = ITEMS.register(
            "wireless_transceiver",
            () -> new BlockItem(ModBlocks.WIRELESS_TRANSCEIVER.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> NETWORK_PATTERN_CONTROLLER = ITEMS.register(
            "network_pattern_controller",
            () -> new BlockItem(ModBlocks.NETWORK_PATTERN_CONTROLLER.get(), new Item.Properties())
    );

    // Crafting Accelerators
    public static final DeferredItem<Item> ACCELERATOR_4x = ITEMS.register(
            "4x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_4x.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> ACCELERATOR_16x = ITEMS.register(
            "16x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_16x.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> ACCELERATOR_64x = ITEMS.register(
            "64x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_64x.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> ACCELERATOR_256x = ITEMS.register(
            "256x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_256x.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> ACCELERATOR_1024x = ITEMS.register(
            "1024x_crafting_accelerator",
            () -> new BlockItem(ModBlocks.ACCELERATOR_1024x.get(), new Item.Properties())
    );

    public static final DeferredItem<EntitySpeedTickerPartItem> ENTITY_TICKER_PART_ITEM = ITEMS.register(
            "entity_speed_ticker",
            () -> new EntitySpeedTickerPartItem(new Item.Properties())
    );

    // AE Upgrade Cards: 实体加速卡（四个等级：x2,x4,x8,x16）
    // 单一实体加速卡 Item（不同等级由 ItemStack.nbt 存储）
    public static final DeferredItem<EntitySpeedCardItem> ENTITY_SPEED_CARD = ITEMS.register(
            "entity_speed_card",
            () -> new EntitySpeedCardItem(new Item.Properties())
    );

    // 频道卡：用于AE机器的无线频道连接
    public static final DeferredItem<ChannelCardItem> CHANNEL_CARD = ITEMS.register(
            "channel_card",
            () -> new ChannelCardItem(new Item.Properties())
    );

    /**
     * 工厂：创建带 multiplier 的实体加速卡 ItemStack（2/4/8/16）
     */
    public static ItemStack createEntitySpeedCardStack(byte multiplier) {
        return EntitySpeedCardItem.withMultiplier(multiplier);
    }

    // 装配矩阵上传核心物品
    public static final DeferredItem<Item> ASSEMBLER_MATRIX_UPLOAD_CORE = ITEMS.register(
            "assembler_matrix_upload_core",
            () -> new BlockItem(ModBlocks.ASSEMBLER_MATRIX_UPLOAD_CORE.get(), new Item.Properties())
    );
}
