package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
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
}
