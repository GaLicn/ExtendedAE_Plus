package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.items.BasicCoreItem;
import com.extendedae_plus.items.EntitySpeedTickerPartItem;
import com.extendedae_plus.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.items.materials.ChannelCardItem;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import com.extendedae_plus.items.materials.ExtendedPatternProviderExpansionCardItem;
import com.extendedae_plus.items.materials.VirtualCraftingCardItem;
import com.extendedae_plus.items.tools.MirrorPatternBindingToolItem;
import com.extendedae_plus.util.ModCheckUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExtendedAEPlus.MODID);
    public static final DeferredItem<Item> WIRELESS_TRANSCEIVER = ITEMS.register(
            "wireless_transceiver",
            () -> new BlockItem(ModBlocks.WIRELESS_TRANSCEIVER.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> LABELED_WIRELESS_TRANSCEIVER = ITEMS.register(
            "labeled_wireless_transceiver",
            () -> new BlockItem(ModBlocks.LABELED_WIRELESS_TRANSCEIVER.get(), new Item.Properties())
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
    public static final DeferredItem<VirtualCraftingCardItem> VIRTUAL_CRAFTING_CARD = ITEMS.register(
            "virtual_crafting_card",
            () -> new VirtualCraftingCardItem(new Item.Properties())
    );
    public static final DeferredItem<ExtendedPatternProviderExpansionCardItem> EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS = ITEMS.register(
            "extended_pattern_provider_expansion_card_plus",
            () -> new ExtendedPatternProviderExpansionCardItem(new Item.Properties())
    );
    // 装配矩阵上传核心物品
    public static final DeferredItem<Item> ASSEMBLER_MATRIX_UPLOAD_CORE = ITEMS.register(
            "assembler_matrix_upload_core",
            () -> new BlockItem(ModBlocks.ASSEMBLER_MATRIX_UPLOAD_CORE.get(), new Item.Properties())
    );
    //超级装配矩阵速度核心
    public static final DeferredItem<Item> ASSEMBLER_MATRIX_SPEED_PLUS = ITEMS.register(
            "assembler_matrix_speed_plus",
            ()->new BlockItem(ModBlocks.ASSEMBLER_MATRIX_SPEED_PLUS.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> ASSEMBLER_MATRIX_CRAFTER_PLUS = ITEMS.register(
            "assembler_matrix_crafter_plus",
            ()->new BlockItem(ModBlocks.ASSEMBLER_MATRIX_CRAFTER_PLUS.get(),  new Item.Properties())
    );
    public static final DeferredItem<Item> ASSEMBLER_MATRIX_PATTERN_PLUS = ITEMS.register(
            "assembler_matrix_pattern_plus",
            () -> new BlockItem(ModBlocks.ASSEMBLER_MATRIX_PATTERN_PLUS.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> SUPER_ASSEMBLER_MATRIX_FRAME = ITEMS.register(
            "super_assembler_matrix_frame",
            () -> new BlockItem(ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> SUPER_ASSEMBLER_MATRIX_WALL = ITEMS.register(
            "super_assembler_matrix_wall",
            () -> new BlockItem(ModBlocks.SUPER_ASSEMBLER_MATRIX_WALL.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> SUPER_ASSEMBLER_MATRIX_GLASS = ITEMS.register(
            "super_assembler_matrix_glass",
            () -> new BlockItem(ModBlocks.SUPER_ASSEMBLER_MATRIX_GLASS.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> MIRROR_PATTERN_PROVIDER = ITEMS.register(
            "mirror_pattern_provider",
            () -> new BlockItem(ModBlocks.MIRROR_PATTERN_PROVIDER_BLOCK.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> C_H716 = ITEMS.register(
            "c-h716",
            () -> new BlockItem(ModBlocks.C_H716.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> FISH_DAN = ITEMS.register(
            "fish_dan_",
            () -> new BlockItem(ModBlocks.FISH_DAN.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> _FENG = ITEMS.register(
            "_feng",
            () -> new BlockItem(ModBlocks._FENG.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> XBAI = ITEMS.register(
            "xbai",
            () -> new BlockItem(ModBlocks.XBAI.get(), new Item.Properties())
    );
    public static final DeferredItem<MirrorPatternBindingToolItem> MIRROR_PATTERN_BINDING_TOOL = ITEMS.register(
            "mirror_pattern_binding_tool",
            () -> new MirrorPatternBindingToolItem(new Item.Properties())
    );
    static final DeferredItem<Item> NETWORK_PATTERN_CONTROLLER = ITEMS.register(
            "network_pattern_controller",
            () -> new BlockItem(ModBlocks.NETWORK_PATTERN_CONTROLLER.get(), new Item.Properties())
    );
    public static final DeferredItem<Item> INFINITY_BIGINTEGER_CELL_ITEM = ITEMS.register(
            "infinity_biginteger_cell", InfinityBigIntegerCellItem::new
    );

    // ==================== 基础核心及相关物品 ====================
    // 基础核心 - 用于合成各种高级核心，稀有度由DataComponent动态设置
    public static final DeferredItem<BasicCoreItem> BASIC_CORE = ITEMS.register(
            "basic_core",
            () -> new BasicCoreItem(new Item.Properties())
    );
    // 存储核心
    public static final DeferredItem<Item> STORAGE_CORE = ITEMS.register(
            "storage_core",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC))
    );
    // 空间核心
    public static final DeferredItem<Item> SPATIAL_CORE = ITEMS.register(
            "spatial_core",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC))
    );
    // 吞噬核心
    public static final DeferredItem<Item> INFINITY_CORE = ITEMS.register(
            "infinity_core",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC))
    );
    // 湮灭奇点
    public static final DeferredItem<Item> OBLIVION_SINGULARITY = ITEMS.register(
            "oblivion_singularity",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.RARE))
    );
    public static final DeferredItem<Item> ENERGY_STORAGE_CORE;
    public static final DeferredItem<Item> QUANTUM_STORAGE_CORE;

    static {
        // 能源存储核心 - 需要AppFlux模组
        if (ModCheckUtils.isAppfluxLoading()) {
            ENERGY_STORAGE_CORE = ITEMS.register(
                    "energy_storage_core",
                    () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC))
            );
        } else {
            ENERGY_STORAGE_CORE = null;
        }

        // 量子存储核心 - 需要AdvancedAE模组
        if (ModCheckUtils.isAAELoading()) {
            QUANTUM_STORAGE_CORE = ITEMS.register(
                    "quantum_storage_core",
                    () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC))
            );
        } else {
            QUANTUM_STORAGE_CORE = null;
        }
    }

    private ModItems() {}

    /**
     * 工厂：创建带 multiplier 的实体加速卡 ItemStack（2/4/8/16）
     */
    static ItemStack createEntitySpeedCardStack(byte multiplier) {
        return EntitySpeedCardItem.withMultiplier(multiplier);
    }
}
