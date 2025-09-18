package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

    public static final DeferredItem<Item> INFINITY_BIGINTEGER_CELL_ITEM = ITEMS.register(
            "infinity_biginteger_cell", InfinityBigIntegerCellItem::new
    );

}
