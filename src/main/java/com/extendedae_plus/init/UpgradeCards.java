package com.extendedae_plus.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;


public class UpgradeCards {
    public UpgradeCards(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 现有：把 Entity Ticker 的部件注册为处理 SPEED/ENERGY 卡的宿主
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.ENTITY_TICKER_PART_ITEM.get(), 8, "group.entity_ticker.name");
            // 使用单一的 UpgradeCard Item 作为注册键，总共允许安装 4 张（不同等级由 ItemStack NBT 区分）
            Upgrades.add(ModItems.ENTITY_SPEED_CARD.get(), ModItems.ENTITY_TICKER_PART_ITEM.get(), 4, "group.entity_ticker.name");

            // 频道卡：对齐旧版注册 — AE2 接口（方块与部件）
            String interfaceGroup = GuiText.Interface.getTranslationKey();
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEBlocks.INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.INTERFACE, 1, interfaceGroup);

            // 频道卡：AE2 样板供应器（方块与部件）
            String patternProviderGroup = "group.pattern_provider.name";
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEBlocks.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.PATTERN_PROVIDER, 1, patternProviderGroup);

            // 频道卡：AE2 I/O 总线与存储总线（部件）
            String ioBusGroup = GuiText.IOBuses.getTranslationKey();
            String storageGroup = "group.storage.name";
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.IMPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.EXPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.STORAGE_BUS, 1, storageGroup);

            // 频道卡：ExtendedAE 扩展组件支持（强依赖，直接引用）
            // 扩展接口
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_INTERFACE_PART, 1, interfaceGroup);
            
            // 扩展样板供应器
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_PATTERN_PROVIDER_PART, 1, patternProviderGroup);
            
            // 扩展 I/O 总线
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_IMPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.EX_EXPORT_BUS, 1, ioBusGroup);
            
            // 其他扩展总线（标签、精确、阈值等）
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.TAG_STORAGE_BUS, 1, storageGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.TAG_EXPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.MOD_STORAGE_BUS, 1, storageGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.MOD_EXPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.PRECISE_STORAGE_BUS, 1, storageGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.PRECISE_EXPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.THRESHOLD_EXPORT_BUS, 1, ioBusGroup);
            
            // 超大接口
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.OVERSIZE_INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), com.glodblock.github.extendedae.common.EAESingletons.OVERSIZE_INTERFACE_PART, 1, interfaceGroup);
        });
    }
}