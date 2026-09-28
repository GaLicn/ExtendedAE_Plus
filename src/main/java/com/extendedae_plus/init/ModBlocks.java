package com.extendedae_plus.init;

import appeng.block.crafting.CraftingUnitBlock;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlock;
import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlock;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlock;
import com.extendedae_plus.content.crystal.LattraBuddingBlock;
import com.extendedae_plus.content.crystal.LattraCrystalClusterBlock;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlock;
import com.extendedae_plus.content.cutter.SuperCircuitCutterBlock;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.content.decor.DollBlock;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlock;
import com.extendedae_plus.content.matrix.HybridCoreBlock;
import com.extendedae_plus.content.matrix.PatternCorePlusBlock;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlock;
import com.extendedae_plus.content.matrix.UploadCoreBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixFrameBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixWallBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixWallBlockEntity;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlock;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExtendedAEPlus.MODID);

    private static BlockBehaviour.Properties properties(Identifier id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
    }

    public static final DeferredBlock<Block> WIRELESS_TRANSCEIVER = BLOCKS.register(
            "wireless_transceiver",
            id -> new WirelessTransceiverBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(2F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> LABELED_WIRELESS_TRANSCEIVER = BLOCKS.register(
            "labeled_wireless_transceiver",
            id -> new LabeledWirelessTransceiverBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // AE2 网络模式控制器方块
    public static final DeferredBlock<Block> NETWORK_PATTERN_CONTROLLER = BLOCKS.register(
            "network_pattern_controller",
            id -> new NetworkPatternControllerBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // Crafting Accelerators (reuse MAE2 textures/models)
    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_4x = BLOCKS.register(
            "4x_crafting_accelerator",
            id -> {
                return new CraftingUnitBlock(properties(id), EPlusCraftingUnitType.ACCELERATOR_4x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_16x = BLOCKS.register(
            "16x_crafting_accelerator",
            id -> {
                return new CraftingUnitBlock(properties(id), EPlusCraftingUnitType.ACCELERATOR_16x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_64x = BLOCKS.register(
            "64x_crafting_accelerator",
            id -> {
                return new CraftingUnitBlock(properties(id), EPlusCraftingUnitType.ACCELERATOR_64x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_256x = BLOCKS.register(
            "256x_crafting_accelerator",
            id -> {
                return new CraftingUnitBlock(properties(id), EPlusCraftingUnitType.ACCELERATOR_256x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_1024x = BLOCKS.register(
            "1024x_crafting_accelerator",
            id -> {
                return new CraftingUnitBlock(properties(id), EPlusCraftingUnitType.ACCELERATOR_1024x);
            }
    );

    // 装配矩阵上传核心方块
    public static final DeferredBlock<UploadCoreBlock> ASSEMBLER_MATRIX_UPLOAD_CORE = BLOCKS.register(
            "assembler_matrix_upload_core",
            id -> new UploadCoreBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );
    public static final DeferredBlock<SpeedCorePlusBlock> ASSEMBLER_MATRIX_SPEED_PLUS = BLOCKS.register(
            "assembler_matrix_speed_plus",
            id -> new SpeedCorePlusBlock(
                    properties(id)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );
    public static final DeferredBlock<CrafterCorePlusBlock> ASSEMBLER_MATRIX_CRAFTER_PLUS = BLOCKS.register(
            "assembler_matrix_crafter_plus",
            id -> new CrafterCorePlusBlock(
                    properties(id)
                            .strength(1.5F,6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<PatternCorePlusBlock> ASSEMBLER_MATRIX_PATTERN_PLUS = BLOCKS.register(
            "assembler_matrix_pattern_plus",
            id -> new PatternCorePlusBlock(
                    properties(id)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // 超级装配矩阵混合核心：一个核心同时提供样板、合成与速度能力。
    public static final DeferredBlock<HybridCoreBlock> ASSEMBLER_MATRIX_HYBRID_PLUS = BLOCKS.register(
            "assembler_matrix_hybrid_plus",
            id -> new HybridCoreBlock(properties(id)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final DeferredBlock<SuperAssemblerMatrixFrameBlock> SUPER_ASSEMBLER_MATRIX_FRAME = BLOCKS.register(
            "super_assembler_matrix_frame",
            id -> new SuperAssemblerMatrixFrameBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<SuperAssemblerMatrixWallBlock<SuperAssemblerMatrixWallBlockEntity>> SUPER_ASSEMBLER_MATRIX_WALL = BLOCKS.register(
            "super_assembler_matrix_wall",
            id -> new SuperAssemblerMatrixWallBlock<>(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<MirrorPatternProviderBlock> MIRROR_PATTERN_PROVIDER_BLOCK = BLOCKS.register(
            "mirror_pattern_provider",
            id -> new MirrorPatternProviderBlock(properties(id))
    );

    public static final DeferredBlock<TagInventoryMEInterfaceBlock> TAG_INVENTORY_ME_INTERFACE = BLOCKS.register(
            "tag_inventory_me_interface",
            id -> new TagInventoryMEInterfaceBlock(
                    properties(id)
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // 单方块超级水晶装配器，使用独立的工作态模型。
    public static final DeferredBlock<SuperCrystalAssemblerBlock> CRYSTAL_ASSEMBLER_PLUS = BLOCKS.register(
            "crystal_assembler_plus",
            id -> new SuperCrystalAssemblerBlock(properties(id)
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final DeferredBlock<SuperCircuitCutterBlock> CIRCUIT_CUTTER_PLUS = BLOCKS.register(
            "circuit_cutter_plus",
            id -> new SuperCircuitCutterBlock(properties(id)
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    // 伪装功能可使用的基础方块，暂不附加额外交互逻辑。
    public static final DeferredBlock<Block> DISGUISED_BLOCK = BLOCKS.register(
            "disguised_block",
            id -> new Block(properties(id)
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final DeferredBlock<Block> C_H716 = registerDollBlock("c-h716");
    public static final DeferredBlock<Block> FISH_DAN = registerDollBlock("fish_dan_");
    public static final DeferredBlock<Block> _LENG = registerDollBlock("_leng");
    public static final DeferredBlock<Block> XBAI = registerDollBlock("xbai");

    public static final DeferredBlock<Block> LATTRA_CRYSTAL_BLOCK = BLOCKS.register(
            "lattra_crystal_block",
            id -> new Block(properties(id)
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F, 8.0F)
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredBlock<LattraBuddingBlock> LATTRA_BUDDING_HARDLY = BLOCKS.register(
            "lattra_budding_hardly",
            id -> new LattraBuddingBlock(buddingProperties(id), LATTRA_CRYSTAL_BLOCK.get())
    );
    public static final DeferredBlock<LattraBuddingBlock> LATTRA_BUDDING_HALF = BLOCKS.register(
            "lattra_budding_half",
            id -> new LattraBuddingBlock(buddingProperties(id), LATTRA_BUDDING_HARDLY.get())
    );
    public static final DeferredBlock<LattraBuddingBlock> LATTRA_BUDDING_MOSTLY = BLOCKS.register(
            "lattra_budding_mostly",
            id -> new LattraBuddingBlock(buddingProperties(id), LATTRA_BUDDING_HALF.get())
    );
    public static final DeferredBlock<LattraBuddingBlock> LATTRA_BUDDING_FULLY = BLOCKS.register(
            "lattra_budding_fully",
            id -> new LattraBuddingBlock(buddingProperties(id), LATTRA_BUDDING_MOSTLY.get())
    );
    public static final DeferredBlock<LattraCrystalClusterBlock> LATTRA_CRYSTAL_BUD_SMALL = registerLattraCrystalBud(
            "lattra_crystal_bud_small", 3, 4, SoundType.SMALL_AMETHYST_BUD, 1
    );
    public static final DeferredBlock<LattraCrystalClusterBlock> LATTRA_CRYSTAL_BUD_MEDIUM = registerLattraCrystalBud(
            "lattra_crystal_bud_medium", 4, 3, SoundType.MEDIUM_AMETHYST_BUD, 2
    );
    public static final DeferredBlock<LattraCrystalClusterBlock> LATTRA_CRYSTAL_BUD_LARGE = registerLattraCrystalBud(
            "lattra_crystal_bud_large", 5, 3, SoundType.LARGE_AMETHYST_BUD, 4
    );
    public static final DeferredBlock<LattraCrystalClusterBlock> LATTRA_CRYSTAL_CLUSTER = registerLattraCrystalBud(
            "lattra_crystal_cluster", 7, 3, SoundType.AMETHYST_CLUSTER, 5
    );

    private static DeferredBlock<Block> registerDollBlock(String name) {
        return BLOCKS.register(
                name,
                id -> new DollBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).setId(ResourceKey.create(Registries.BLOCK, id)).noOcclusion())
        );
    }

    private static BlockBehaviour.Properties buddingProperties(Identifier id) {
        return properties(id)
                .mapColor(MapColor.GOLD)
                .strength(3.0F, 8.0F)
                .sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()
                .randomTicks();
    }

    private static DeferredBlock<LattraCrystalClusterBlock> registerLattraCrystalBud(
            String name, int height, int width, SoundType sound, int lightLevel) {
        return BLOCKS.register(name, id -> new LattraCrystalClusterBlock(height, width,
                properties(id)
                        .mapColor(MapColor.GOLD)
                        .strength(1.5F)
                        .sound(sound)
                        .lightLevel(state -> lightLevel)
                        .noOcclusion()
                        .requiresCorrectToolForDrops()));
    }
}
