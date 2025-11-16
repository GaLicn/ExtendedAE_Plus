package com.extendedae_plus.init;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.extendedae_plus.content.matrix.UploadCoreBlock;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ExtendedAEPlus.MODID);

    public static final RegistryObject<Block> WIRELESS_TRANSCEIVER = BLOCKS.register(
            "wireless_transceiver",
            () -> new WirelessTransceiverBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // AE2 网络模式控制器方块
    public static final RegistryObject<Block> NETWORK_PATTERN_CONTROLLER = BLOCKS.register(
            "network_pattern_controller",
            () -> new com.extendedae_plus.content.controller.NetworkPatternControllerBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    // 装配矩阵上传核心（内部功能块）
    public static final RegistryObject<UploadCoreBlock> ASSEMBLER_MATRIX_UPLOAD_CORE = BLOCKS.register(
            "assembler_matrix_upload_core",
            () -> {
                var b = new UploadCoreBlock();
                // 注意：方块实体绑定延后到 commonSetup 的 enqueueWork 中执行，避免注册阶段循环依赖
                return b;
            }
    );

    // Crafting Accelerators (reuse MAE2 textures/models)
    public static final RegistryObject<CraftingUnitBlock> CRAFTING_ACCELERATOR_4x = BLOCKS.register(
            "4x_crafting_accelerator",
            () -> {
                var b = new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_4x);
                b.setBlockEntity(CraftingBlockEntity.class, AEBlockEntities.CRAFTING_UNIT, null, null);
                return b;
            }
    );

    public static final RegistryObject<CraftingUnitBlock> CRAFTING_ACCELERATOR_16x = BLOCKS.register(
            "16x_crafting_accelerator",
            () -> {
                var b = new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_16x);
                b.setBlockEntity(CraftingBlockEntity.class, AEBlockEntities.CRAFTING_UNIT, null, null);
                return b;
            }
    );

    public static final RegistryObject<CraftingUnitBlock> CRAFTING_ACCELERATOR_64x = BLOCKS.register(
            "64x_crafting_accelerator",
            () -> {
                var b = new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_64x);
                b.setBlockEntity(CraftingBlockEntity.class, AEBlockEntities.CRAFTING_UNIT, null, null);
                return b;
            }
    );

    public static final RegistryObject<CraftingUnitBlock> CRAFTING_ACCELERATOR_256x = BLOCKS.register(
            "256x_crafting_accelerator",
            () -> {
                var b = new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_256x);
                b.setBlockEntity(CraftingBlockEntity.class, AEBlockEntities.CRAFTING_UNIT, null, null);
                return b;
            }
    );

    public static final RegistryObject<CraftingUnitBlock> CRAFTING_ACCELERATOR_1024x = BLOCKS.register(
            "1024x_crafting_accelerator",
            () -> {
                var b = new CraftingUnitBlock(EPlusCraftingUnitType.ACCELERATOR_1024x);
                b.setBlockEntity(CraftingBlockEntity.class, AEBlockEntities.CRAFTING_UNIT, null, null);
                return b;
            }
    );
}
