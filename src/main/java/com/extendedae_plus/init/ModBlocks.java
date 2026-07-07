package com.extendedae_plus.init;

import appeng.block.crafting.CraftingUnitBlock;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlock;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlock;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.content.decor.DollBlock;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlock;
import com.extendedae_plus.content.matrix.PatternCorePlusBlock;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlock;
import com.extendedae_plus.content.matrix.UploadCoreBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixFrameBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixGlassBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixWallBlock;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixWallBlockEntity;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlock;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExtendedAEPlus.MODID);

    public static final DeferredBlock<Block> WIRELESS_TRANSCEIVER = BLOCKS.register(
            "wireless_transceiver",
            () -> new WirelessTransceiverBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> LABELED_WIRELESS_TRANSCEIVER = BLOCKS.register(
            "labeled_wireless_transceiver",
            () -> new LabeledWirelessTransceiverBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // AE2 网络模式控制器方块
    public static final DeferredBlock<Block> NETWORK_PATTERN_CONTROLLER = BLOCKS.register(
            "network_pattern_controller",
            () -> new NetworkPatternControllerBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // Crafting Accelerators (reuse MAE2 textures/models)
    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_4x = BLOCKS.register(
            "4x_crafting_accelerator",
            () -> {
                return new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_4x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_16x = BLOCKS.register(
            "16x_crafting_accelerator",
            () -> {
                return new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_16x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_64x = BLOCKS.register(
            "64x_crafting_accelerator",
            () -> {
                return new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_64x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_256x = BLOCKS.register(
            "256x_crafting_accelerator",
            () -> {
                return new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_256x);
            }
    );

    public static final DeferredBlock<CraftingUnitBlock> ACCELERATOR_1024x = BLOCKS.register(
            "1024x_crafting_accelerator",
            () -> {
                return new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_1024x);
            }
    );

    // 装配矩阵上传核心方块
    public static final DeferredBlock<UploadCoreBlock> ASSEMBLER_MATRIX_UPLOAD_CORE = BLOCKS.register(
            "assembler_matrix_upload_core",
            () -> new UploadCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );
    public static final DeferredBlock<SpeedCorePlusBlock> ASSEMBLER_MATRIX_SPEED_PLUS = BLOCKS.register(
            "assembler_matrix_speed_plus",
            () -> new SpeedCorePlusBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );
    public static final DeferredBlock<CrafterCorePlusBlock> ASSEMBLER_MATRIX_CRAFTER_PLUS = BLOCKS.register(
            "assembler_matrix_crafter_plus",
            () -> new CrafterCorePlusBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5F,6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<PatternCorePlusBlock> ASSEMBLER_MATRIX_PATTERN_PLUS = BLOCKS.register(
            "assembler_matrix_pattern_plus",
            () -> new PatternCorePlusBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<SuperAssemblerMatrixFrameBlock> SUPER_ASSEMBLER_MATRIX_FRAME = BLOCKS.register(
            "super_assembler_matrix_frame",
            () -> new SuperAssemblerMatrixFrameBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<SuperAssemblerMatrixWallBlock<SuperAssemblerMatrixWallBlockEntity>> SUPER_ASSEMBLER_MATRIX_WALL = BLOCKS.register(
            "super_assembler_matrix_wall",
            () -> new SuperAssemblerMatrixWallBlock<>(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<SuperAssemblerMatrixGlassBlock> SUPER_ASSEMBLER_MATRIX_GLASS = BLOCKS.register(
            "super_assembler_matrix_glass",
            () -> new SuperAssemblerMatrixGlassBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<MirrorPatternProviderBlock> MIRROR_PATTERN_PROVIDER_BLOCK = BLOCKS.register(
            "mirror_pattern_provider",
            MirrorPatternProviderBlock::new
    );

    public static final DeferredBlock<Block> C_H716 = registerDollBlock("c-h716");
    public static final DeferredBlock<Block> FISH_DAN = registerDollBlock("fish_dan_");
    public static final DeferredBlock<Block> _FENG = registerDollBlock("_feng");
    public static final DeferredBlock<Block> XBAI = registerDollBlock("xbai");

    private static DeferredBlock<Block> registerDollBlock(String name) {
        return BLOCKS.register(
                name,
                () -> new DollBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion())
        );
    }
}
