package com.extendedae_plus.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;


public class UpgradeCards {
    public UpgradeCards(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ENTITY_TICKER_PART_ITEM.get(), 8, "group.entity_ticker.name");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.ENTITY_TICKER_PART_ITEM.get(), 8, "group.entity_ticker.name");
        });
    }
}