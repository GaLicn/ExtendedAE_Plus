package com.extendedae_plus.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.level.ItemLike;

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
            // 超级切片机与原机相同，允许安装四张 AE2 速度卡。
            Upgrades.add(AEItems.SPEED_CARD, ModItems.CIRCUIT_CUTTER_PLUS.get(), 4, "group.circuit_cutter_plus.name");

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
            Upgrades.add(ModItems.EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS.get(), EX_PATTERN_PROVIDER, 3, patternProviderGroup);
            Upgrades.add(ModItems.EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS.get(), EX_PATTERN_PROVIDER_PART, 3, patternProviderGroup);

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

            String storageCellGroup = GuiText.StorageCells.getTranslationKey();
            Upgrades.add(AEItems.FUZZY_CARD, ModItems.INFINITY_BIGINTEGER_CELL.get(), 1, storageCellGroup);
            Upgrades.add(AEItems.INVERTER_CARD, ModItems.INFINITY_BIGINTEGER_CELL.get(), 1, storageCellGroup);

            //EAE 的扩展输入/输出总线支持频道卡（部件）
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_IMPORT_BUS, 1, ioBusGroup);
            Upgrades.add(ModItems.CHANNEL_CARD.get(), EX_EXPORT_BUS, 1, ioBusGroup);

            // AdvancedAE 的大小型高级样板供应器共用同一套频道卡逻辑。
            if (ModList.get().isLoaded("advanced_ae")) {
                registerAdvancedPatternProviderCards();
            }
        });
    }

    /** 用反射读取可选模组定义，避免未安装 AdvancedAE 时加载失败。 */
    private static void registerAdvancedPatternProviderCards() {
        String group = "group.pattern_provider.name";
        String[] blockFields = {"ADV_PATTERN_PROVIDER", "SMALL_ADV_PATTERN_PROVIDER"};
        String[] itemFields = {"ADV_PATTERN_PROVIDER", "SMALL_ADV_PATTERN_PROVIDER"};
        try {
            Class<?> blocks = Class.forName("net.pedroksl.advanced_ae.common.definitions.AAEBlocks");
            Class<?> items = Class.forName("net.pedroksl.advanced_ae.common.definitions.AAEItems");
            for (String fieldName : blockFields) {
                Object definition = blocks.getField(fieldName).get(null);
                if (definition instanceof ItemLike itemLike) {
                    Upgrades.add(ModItems.CHANNEL_CARD.get(), itemLike, 1, group);
                }
            }
            for (String fieldName : itemFields) {
                Object definition = items.getField(fieldName).get(null);
                if (definition instanceof ItemLike itemLike) {
                    Upgrades.add(ModItems.CHANNEL_CARD.get(), itemLike, 1, group);
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            com.extendedae_plus.util.Logger.EAP$LOGGER.warn("AdvancedAE 频道卡注册跳过：未找到供应器定义", e);
        }
    }
}
