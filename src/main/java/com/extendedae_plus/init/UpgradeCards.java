package com.extendedae_plus.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static com.glodblock.github.extendedae.common.EPPItemAndBlock.*;

/**
 * 
 */
public final class UpgradeCards {
    public UpgradeCards(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 现有：把 Entity Ticker 的部件注册为处理 SPEED/ENERGY/CHANNEL 卡的宿主
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.ENTITY_TICKER_PART_ITEM.get(), 8, "group.entity_ticker.name");
            // 使用单一的 UpgradeCard Item 作为注册键，总共允许安装 4 张（不同等级由 ItemStack NBT 区分）
            Upgrades.add(ModItems.ENTITY_SPEED_CARD.get(), ModItems.ENTITY_TICKER_PART_ITEM.get(), 4, "group.entity_ticker.name");
            Upgrades.add(ModItems.CHANNEL_CARD.get(), ModItems.ENTITY_TICKER_PART_ITEM.get(), 1, "group.entity_ticker.name");

            // 新增：频道卡仅允许安装在 ME 接口（方块与部件）上，每台最多 1 张
            String interfaceGroup = GuiText.Interface.getTranslationKey();
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEBlocks.INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.INTERFACE, 1, interfaceGroup);

            // 新增：样板供应器（方块与部件）支持频道卡、虚拟合成卡，每台最多 1 张
            String patternProviderGroup = "group.pattern_provider.name";
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEBlocks.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.VIRTUAL_CRAFTING_CARD.get(), AEBlocks.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.VIRTUAL_CRAFTING_CARD.get(), AEParts.PATTERN_PROVIDER, 1, patternProviderGroup);

            // ExtendedAE 的扩展样板供应器（方块与部件）
            Upgrades.add(ModItems.CHANNEL_CARD.get(),EX_PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(),EX_PATTERN_PROVIDER_PART, 1, patternProviderGroup);
            Upgrades.add(ModItems.VIRTUAL_CRAFTING_CARD.get(),EX_PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(ModItems.VIRTUAL_CRAFTING_CARD.get(),EX_PATTERN_PROVIDER_PART, 1, patternProviderGroup);

            //EAE 的扩展接口与超大接口（方块与部件）支持频道卡
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_INTERFACE_PART, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), OVERSIZE_INTERFACE, 1, interfaceGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), OVERSIZE_INTERFACE_PART, 1, interfaceGroup);

            //AE2 的输入/输出/存储总线支持频道卡（部件）
            String ioBusGroup = GuiText.IOBuses.getTranslationKey();
            String storageGroup = "group.storage.name";
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.IMPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.EXPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), AEParts.STORAGE_BUS, 1, storageGroup);

            //EAE 的扩展输入/输出总线支持频道卡（部件）
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_IMPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_EXPORT_BUS, 1, ioBusGroup);
        });
    }
}