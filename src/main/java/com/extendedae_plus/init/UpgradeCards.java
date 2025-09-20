package com.extendedae_plus.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;


public class UpgradeCards {
    public UpgradeCards(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 现有：把 Entity Ticker 的部件注册为处理 SPEED/ENERGY 卡的宿主
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.ENTITY_TICKER_PART_ITEM.get(), 8, "group.entity_ticker.name");
            // 使用单一的 UpgradeCard Item 作为注册键，总共允许安装 4 张（不同等级由 ItemStack NBT 区分）
            Upgrades.add(ModItems.ENTITY_SPEED_CARD.get(), ModItems.ENTITY_TICKER_PART_ITEM.get(), 4, "group.entity_ticker.name");
        });
    }
}